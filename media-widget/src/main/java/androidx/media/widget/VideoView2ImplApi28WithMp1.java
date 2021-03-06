/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media.widget;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnSubtitleDataListener;
import android.media.SubtitleData;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media2.SubtitleData2;
import androidx.media2.subtitle.Cea708CaptionRenderer;
import androidx.media2.subtitle.ClosedCaptionRenderer;
import androidx.media2.subtitle.SubtitleController;
import androidx.media2.subtitle.SubtitleTrack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/**
 * Base implementation of VideoView2.
 */
@RequiresApi(28)
class VideoView2ImplApi28WithMp1 extends VideoView2ImplBaseWithMp1 {
    private static final String TAG = "VideoView2ImplApi28_1";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int INVALID_TRACK_INDEX = -1;

    ArrayList<Pair<Integer, SubtitleTrack>> mSubtitleTrackIndices;
    private SubtitleController mSubtitleController;

    // selected video/audio/subtitle track index as MediaPlayer returns
    int mSelectedSubtitleTrackIndex;

    private SubtitleAnchorView mSubtitleAnchorView;

    @Override
    public void initialize(
            VideoView2 instance, Context context,
            @Nullable AttributeSet attrs, int defStyleAttr) {
        super.initialize(instance, context, attrs, defStyleAttr);
        mSelectedSubtitleTrackIndex = INVALID_TRACK_INDEX;

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        mSubtitleAnchorView = new SubtitleAnchorView(context);
        mSubtitleAnchorView.setLayoutParams(params);
        mSubtitleAnchorView.setBackgroundColor(0);
        mInstance.addView(mSubtitleAnchorView);
    }

    ///////////////////////////////////////////////////
    // Protected or private methods
    ///////////////////////////////////////////////////

    /**
     * Used in openVideo(). Setup MediaPlayer and related objects before calling prepare.
     */
    @Override
    protected void setupMediaPlayer(Context context, Uri uri, Map<String, String> headers)
            throws IOException {
        super.setupMediaPlayer(context, uri, headers);

        mSubtitleController = new SubtitleController(context);
        mSubtitleController.registerRenderer(new ClosedCaptionRenderer(context));
        mSubtitleController.registerRenderer(new Cea708CaptionRenderer(context));
        mSubtitleController.setAnchor((SubtitleController.Anchor) mSubtitleAnchorView);

        mMediaPlayer.setOnSubtitleDataListener(mSubtitleListener);
    }

    @Override
    protected void extractTracks() {
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        mVideoTrackIndices = new ArrayList<>();
        mAudioTrackIndices = new ArrayList<>();
        mSubtitleTrackIndices = new ArrayList<>();
        mSubtitleController.reset();
        for (int i = 0; i < trackInfos.length; ++i) {
            int trackType = trackInfos[i].getTrackType();
            if (trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                mVideoTrackIndices.add(i);
            } else if (trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                mAudioTrackIndices.add(i);
            } else if (trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                SubtitleTrack track = mSubtitleController.addTrack(trackInfos[i].getFormat());
                if (track != null) {
                    mSubtitleTrackIndices.add(new Pair<>(i, track));
                }
            }
        }
        // Select first tracks as default
        if (mVideoTrackIndices.size() > 0) {
            mSelectedVideoTrackIndex = 0;
        }
        if (mAudioTrackIndices.size() > 0) {
            mSelectedAudioTrackIndex = 0;
        }

        Bundle data = new Bundle();
        data.putInt(MediaControlView2.KEY_VIDEO_TRACK_COUNT, mVideoTrackIndices.size());
        data.putInt(MediaControlView2.KEY_AUDIO_TRACK_COUNT, mAudioTrackIndices.size());
        data.putInt(MediaControlView2.KEY_SUBTITLE_TRACK_COUNT, mSubtitleTrackIndices.size());
        mMediaSession.sendSessionEvent(MediaControlView2.EVENT_UPDATE_TRACK_STATUS, data);
    }

    private OnSubtitleDataListener mSubtitleListener =
            new OnSubtitleDataListener() {
                @Override
                public void onSubtitleData(MediaPlayer mp, SubtitleData data) {
                    if (DEBUG) {
                        Log.d(TAG, "onSubtitleData(): getTrackIndex: " + data.getTrackIndex()
                                + ", getCurrentPosition: " + mp.getCurrentPosition()
                                + ", getStartTimeUs(): " + data.getStartTimeUs()
                                + ", diff: "
                                + (data.getStartTimeUs() / 1000 - mp.getCurrentPosition())
                                + "ms, getDurationUs(): " + data.getDurationUs());

                    }
                    final int index = data.getTrackIndex();
                    if (index != mSelectedSubtitleTrackIndex) {
                        Log.d(TAG, "onSubtitleData(): getTrackIndex: " + data.getTrackIndex()
                                + ", selected track index: " + mSelectedSubtitleTrackIndex);
                        return;
                    }
                    for (Pair<Integer, SubtitleTrack> p : mSubtitleTrackIndices) {
                        if (p.first == index) {
                            SubtitleTrack track = p.second;
                            track.onData(new SubtitleData2(data));
                        }
                    }
                }
            };

    @Override
    protected void doShowSubtitleCommand(Bundle args) {
        int subtitleIndex = args != null ? args.getInt(
                MediaControlView2.KEY_SELECTED_SUBTITLE_INDEX,
                INVALID_TRACK_INDEX) : INVALID_TRACK_INDEX;
        if (subtitleIndex != INVALID_TRACK_INDEX) {
            int subtitleTrackIndex = mSubtitleTrackIndices.get(subtitleIndex).first;
            if (subtitleTrackIndex != mSelectedSubtitleTrackIndex) {
                selectSubtitleTrack(subtitleTrackIndex);
            }
        }
    }

    @Override
    protected void doHideSubtitleCommand() {
        deselectSubtitleTrack();
    }

    private void selectSubtitleTrack(int trackIndex) {
        if (!isInPlaybackState()) {
            return;
        }
        for (Pair<Integer, SubtitleTrack> p : mSubtitleTrackIndices) {
            if (p.first == trackIndex) {
                mMediaPlayer.selectTrack(trackIndex);
                SubtitleTrack track = p.second;
                mSubtitleController.selectTrack(track);
                mSelectedSubtitleTrackIndex = trackIndex;
                mSubtitleAnchorView.setVisibility(View.VISIBLE);
                break;
            }
        }
    }

    private void deselectSubtitleTrack() {
        if (!isInPlaybackState() || mSelectedSubtitleTrackIndex == INVALID_TRACK_INDEX) {
            return;
        }
        mMediaPlayer.deselectTrack(mSelectedSubtitleTrackIndex);
        mSelectedSubtitleTrackIndex = INVALID_TRACK_INDEX;
        mSubtitleAnchorView.setVisibility(View.GONE);
    }
}
