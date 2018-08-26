/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.pluginsample;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.FileObserver;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.lang.System.currentTimeMillis;

/**
 * MainFragment
 */
public class MainFragment extends Fragment {
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private int mCameraId;
    private Camera.Parameters mParameters;
    private Camera.CameraInfo mCameraInfo;

    private AudioManager mAudioManager;//for video
    private MediaRecorder mMediaRecorder;//for video

    private boolean isSurface = false;
    private boolean isCapturing = false;
    private File instanceRecordMP4;
    private File instanceRecordWAV;

    private final String fileDir = "/storage/emulated/0/DCIM/";

    private MediaRecorder.OnErrorListener onErrorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mediaRecorder, int what, int extra) {
        }
    };

    private MediaRecorder.OnInfoListener onInfoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra){
        }
    };

    private FileObserver fileObserver = new FileObserver(fileDir) {
        @Override
        public void onEvent(int event, String path) {
            switch( event ){
                case FileObserver.OPEN:
                    Log.d("debug","OPEN:" + path );
                    break;
                case FileObserver.CLOSE_NOWRITE:
                    Log.d("debug","CLOSE:" + path);
                    break;
                case FileObserver.CREATE:
                    Log.d("debug","CREATE:" + path);
                    break;
                case FileObserver.DELETE:
                    Log.d("debug", "DELETE:" + path);
                    break;
                case FileObserver.CLOSE_WRITE:
                    Log.d("debug", "CLOSE_WRITE:" + path);
                    break;
                case FileObserver.MODIFY:
                    //Log.d("debug", "MODIFY:" + path);
                    break;
                default:
                    Log.d("debug", "event:" + event + ", " + path);
                    break;
            }
        }
    };

    public void startWatching(){
        fileObserver.startWatching();
    }

    public void stopWatching(){
        fileObserver.stopWatching();
    }

    public MainFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        SurfaceView surfaceView = (SurfaceView) view.findViewById(R.id.surfaceView);
        mSurfaceHolder = surfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceHolderCallback);

        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);//for video
    }


    @Override
    public void onStart() {
        super.onStart();
//        startWatching(); // for debug

        if (isSurface) {
            open();
            setSurface(mSurfaceHolder);
        }
    }

    @Override
    public void onStop() {
//        stopWatching(); // for debug

        close();
        super.onStop();
    }

    private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            isSurface = true;
            open();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
            setSurface(surfaceHolder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            isSurface = false;
            close();
        }
    };

    public void takePicture() {
        if(!isCapturing) {
            isCapturing = true;

            mParameters.setPictureSize(5376, 2688);
            mParameters.set("RIC_SHOOTING_MODE", "RicStillCaptureStd");
            mParameters.set("RIC_EXPOSURE_MODE", "RicAutoExposureP");
            mParameters.set("RIC_PROC_STITCHING", "RicDynamicStitchingAuto");
            mParameters.set("recording-hint", "false");
            mParameters.setJpegThumbnailSize(320, 160);
            mCamera.setParameters(mParameters);

            mCamera.takePicture(onShutterCallback, null, onJpegPictureCallback);
            Log.d("debug","mCamera.takePicture()" );
        }
    }

    public boolean isMediaRecorder() {
        return mMediaRecorder == null;
    }

    public boolean takeVideo() {
        boolean result = true;
        if (mMediaRecorder == null) {
            mAudioManager.setParameters("RicUseBFormat=true");
            mAudioManager.setParameters("RicMicSelect=RicMicSelectAuto");
            mAudioManager
                    .setParameters("RicMicSurroundVolumeLevel=RicMicSurroundVolumeLevelNormal");

            mParameters.set("RIC_PROC_STITCHING", "RicStaticStitching");
            mParameters.set("RIC_SHOOTING_MODE", "RicMovieRecording4kEqui");

            CamcorderProfile camcorderProfile = CamcorderProfile.get(mCameraId, 10013); // for 4K video

            mParameters.set("video-size", "3840x1920");
            mParameters.set("recording-hint", "true");

            mCamera.setParameters(mParameters);

            mMediaRecorder = new MediaRecorder();
            mCamera.unlock();

            mMediaRecorder.setCamera(mCamera);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);

            camcorderProfile.videoCodec = MediaRecorder.VideoEncoder.H264;
            camcorderProfile.audioCodec = MediaRecorder.AudioEncoder.AAC;
            camcorderProfile.audioChannels = 1;

            mMediaRecorder.setProfile(camcorderProfile);
            mMediaRecorder.setVideoEncodingBitRate(56000000); // 56 Mbps
            mMediaRecorder.setVideoFrameRate(30); // 30 fps
            mMediaRecorder.setMaxDuration(1500000); // max: 25 min
            mMediaRecorder.setMaxFileSize(20401094656L); // max: 19 GB

            String videoFile = fileDir + "plugin" + getDateTime() + ".mp4";
            String wavFile = fileDir + "plugin" + getDateTime() + ".wav";
            String videoWavFile = String.format("%s,%s", videoFile, wavFile);
            mMediaRecorder.setOutputFile(videoWavFile);
            mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
            mMediaRecorder.setOnErrorListener(onErrorListener);
            mMediaRecorder.setOnInfoListener(onInfoListener);

            try {
                mMediaRecorder.prepare();
                mMediaRecorder.start();
                Log.d("debug","mMediaRecorder.start()" );

                instanceRecordMP4 = new File(videoFile);
                instanceRecordWAV = new File(wavFile);
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
                result = false;
            }
        } else {
            try {
                mMediaRecorder.stop();
                Log.d("debug","mMediaRecorder.stop()" );
            } catch(RuntimeException e) {
                // cancel recording
                instanceRecordMP4.delete();
                instanceRecordWAV.delete();
                result = false;
            } finally {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
                mCamera.lock();
            }
        }
        return result;
    }

    private void open() {
        if (mCamera == null) {
            int numberOfCameras = Camera.getNumberOfCameras();

            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);

                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCameraInfo = info;
                    mCameraId = i;
                }

                mCamera = Camera.open(mCameraId);
            }
            mCamera.setErrorCallback(mErrorCallback);
            mParameters = mCamera.getParameters();

            mParameters.set("RIC_SHOOTING_MODE", "RicMonitoring");
            mCamera.setParameters(mParameters);
        }
    }

    public void close() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.setErrorCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private void setSurface(@NonNull SurfaceHolder surfaceHolder) {
        if (mCamera != null) {
            mCamera.stopPreview();

            try {
                mCamera.setPreviewDisplay(surfaceHolder);
                mParameters.setPreviewSize(1920, 960);
                mCamera.setParameters(mParameters);
            } catch (IOException e) {
                e.printStackTrace();
                close();
            }
            mCamera.startPreview();
        }
    }

    private Camera.ErrorCallback mErrorCallback = new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {

        }
    };

    private Camera.ShutterCallback onShutterCallback = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
        }
    };

    private Camera.PictureCallback onJpegPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            mParameters.set("RIC_PROC_STITCHING", "RicStaticStitching");
            mCamera.setParameters(mParameters);
            mCamera.stopPreview();

            String fileUrl = fileDir + "plugin" + getDateTime() + ".jpg";
            try (FileOutputStream fileOutputStream = new FileOutputStream(fileUrl)) {
                fileOutputStream.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }

            mCamera.startPreview();
            isCapturing = false;
        }
    };

    private String getDateTime(){
        Date date = new Date( System.currentTimeMillis() );

        String format = "yyyyMMddHHmmss";
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        String text = sdf.format(date);
        return text;
    }

    public boolean isCapturing() {
        return isCapturing;
    }
}
