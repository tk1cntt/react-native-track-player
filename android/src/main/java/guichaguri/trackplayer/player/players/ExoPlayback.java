package guichaguri.trackplayer.player.players;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import com.facebook.react.bridge.Promise;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import guichaguri.trackplayer.logic.MediaManager;
import guichaguri.trackplayer.logic.Utils;
import guichaguri.trackplayer.logic.track.Track;
import guichaguri.trackplayer.logic.track.TrackType;
import guichaguri.trackplayer.player.Playback;
import java.io.File;

import static com.google.android.exoplayer2.DefaultLoadControl.*;

/**
 * Feature-rich player using {@link SimpleExoPlayer}
 *
 * @author Guilherme Chaguri
 */
public class ExoPlayback extends Playback implements EventListener {

    private final SimpleExoPlayer player;
    private final long cacheMaxSize;

    private Promise loadCallback = null;
    private boolean playing = false;

    public ExoPlayback(Context context, MediaManager manager, Bundle options) {
        super(context, manager);

        int minBuffer = (int)Utils.toMillis(options.getDouble("minBuffer", Utils.toSeconds(DEFAULT_MIN_BUFFER_MS)));
        int maxBuffer = (int)Utils.toMillis(options.getDouble("maxBuffer", Utils.toSeconds(DEFAULT_MAX_BUFFER_MS)));
        int playBuffer = (int)Utils.toMillis(options.getDouble("playBuffer", Utils.toSeconds(DEFAULT_BUFFER_FOR_PLAYBACK_MS)));
        int multiplier = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / DEFAULT_BUFFER_FOR_PLAYBACK_MS;

        DefaultAllocator allocator = new DefaultAllocator(true, 0x10000);
        LoadControl control = new DefaultLoadControl(allocator, minBuffer, maxBuffer, playBuffer, playBuffer * multiplier, -1, true);

        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(context), new DefaultTrackSelector(), control);
        player.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build());
        player.addListener(this);

        cacheMaxSize = (long)(options.getDouble("maxCacheSize", 0) * 1024);
    }

    @Override
    public void load(Track track, Promise callback) {
        loadCallback = callback;

        Uri url = track.url;

        String userAgent = Util.getUserAgent(context, "react-native-track-player");
        DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
            userAgent,
            null,
            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
            true
        );
        DataSource.Factory factory = new DefaultDataSourceFactory(context, null, httpDataSourceFactory);
        MediaSource source;

        if(cacheMaxSize > 0 && !track.urlLocal) {
            File cacheDir = new File(context.getCacheDir(), "TrackPlayer");
            Cache cache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(cacheMaxSize));
            factory = new CacheDataSourceFactory(cache, factory, 0, cacheMaxSize);
        }

        if(track.type == TrackType.DASH) {
            source = new DashMediaSource(url, factory, new DefaultDashChunkSource.Factory(factory), null, null);
        } else if(track.type == TrackType.HLS) {
            source = new HlsMediaSource(url, factory, null, null);
        } else if(track.type == TrackType.SMOOTH_STREAMING) {
            source = new SsMediaSource(url, factory, new DefaultSsChunkSource.Factory(factory), null, null);
        } else {
            source = new ExtractorMediaSource(url, factory, new DefaultExtractorsFactory(), null, null);
        }

        player.prepare(source);
    }

    @Override
    public void reset() {
        super.reset();
        player.stop();
    }

    @Override
    public void play() {
        player.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        player.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public int getState() {
        return getState(player.getPlaybackState());
    }

    private int getState(int playerState) {
        switch(playerState) {
            case Player.STATE_BUFFERING:
                return PlaybackStateCompat.STATE_BUFFERING;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_STOPPED;
            case Player.STATE_IDLE:
                return PlaybackStateCompat.STATE_NONE;
            case Player.STATE_READY:
                return playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        }
        return PlaybackStateCompat.STATE_NONE;
    }

    @Override
    public long getPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    @Override
    public long getDuration() {
        return player.getDuration();
    }

    @Override
    public void seekTo(long ms) {
        player.seekTo(ms);
    }

    @Override
    public float getRate() {
        return player.getPlaybackParameters().speed;
    }

    @Override
    public void setRate(float rate) {
        PlaybackParameters params = player.getPlaybackParameters();
        player.setPlaybackParameters(new PlaybackParameters(rate, params.pitch));
    }

    @Override
    public float getVolume() {
        return player.getVolume();
    }

    @Override
    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public void destroy() {
        player.release();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object o, int i) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean loading) {
        updateState(loading ? PlaybackStateCompat.STATE_BUFFERING : getState());
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        playing = playWhenReady;
        updateState(getState(playbackState));

        if(playbackState == Player.STATE_READY) {

            Utils.resolveCallback(loadCallback);
            loadCallback = null;

        } else if(playbackState == Player.STATE_ENDED) {

            if(hasNext()) {
                updateCurrentTrack(currentTrack + 1, null);
            } else {
                manager.onEnd(getCurrentTrack(), getPosition());
            }

        }
    }

    @Override
    public void onRepeatModeChanged(int i) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean b) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Utils.rejectCallback(loadCallback, error);
        loadCallback = null;

        manager.onError(error);
    }

    @Override
    public void onPositionDiscontinuity(int i) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }

}
