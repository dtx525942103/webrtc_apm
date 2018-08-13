package com.google.webrtc.apmdemo;

import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.webrtc.apm.Faac;
import com.google.webrtc.apm.Ticker;
import com.google.webrtc.apm.WebRtcJni.WebRtcVad;
import com.google.webrtc.apm.WebRtcJni.WebRtcNs;
import com.google.webrtc.apm.WebRtcJni.WebRtcAecm;
import com.google.webrtc.apm.WebRtcJni.WebRtcAgc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.datatype.Duration;

public class MainActivity extends AppCompatActivity implements AudioCapturer.OnAudioCapturedListener{

    Switch sw_record;
    TextView lb_vad_status;
    Button bt_origin,bt_ns,bt_agc,bt_ns_agc,bt_agc_ns;

    AudioCapturer audioCapturer = new AudioCapturer();
    AudioPlayer audioPlayer = new AudioPlayer();

    WebRtcVad vad = new WebRtcVad(2);
    WebRtcNs ns = new WebRtcNs(16000,1);
    WebRtcAecm aecm = new WebRtcAecm(16000,false,3);
    WebRtcAgc agc = new WebRtcAgc(0,255,3,16000);

    Handler handler = new Handler();
    ArrayList<short[]> pcmDataArr = new ArrayList<>();
    TaskQuenu taskQuenu = new TaskQuenu();

    //每个切片20ms的pcm数据
    final static int PCM_SLICE_MS = 10;
    BufferSlice bufferSlice = new BufferSlice(16000 * PCM_SLICE_MS / 1000);
    boolean interrupted = false;
    Faac faac = new Faac(16000,1);
    FileOutputStream file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sw_record = findViewById(R.id.sw_record);
        lb_vad_status = findViewById(R.id.lb_vad_status);
        bt_agc = findViewById(R.id.bt_agc);
        bt_agc_ns = findViewById(R.id.bt_agc_ns);
        bt_ns_agc = findViewById(R.id.bt_ns_agc);

        bt_ns = findViewById(R.id.bt_ns);
        bt_origin = findViewById(R.id.bt_origin);
        agc.setConfig(3,30,true);

    }

    public void onClick_record(View view) {
        if (sw_record.isChecked()){
            pcmDataArr.clear();
            bufferSlice.clear();
            audioCapturer.setOnAudioCapturedListener(this);
            try {
                file =  new FileOutputStream(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "1.aac");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            audioCapturer.startCapture();
        }else {
            audioCapturer.stopCapture();
            try {
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public void onAudioCaptured(short[] audioData, int stamp) {
        byte [] aac = faac.encode(audioData);
        if(aac != null){
            try {
                file.write(aac);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        bufferSlice.input(audioData, audioData.length, stamp, audioData.length * 1000/ 16000, new BufferSlice.ISliceOutput() {
            @Override
            public void onOutput(short[] slice, int stamp) {
                //bufferSlice内部的切片缓存(slice)是复用的，所以需要拷贝出来防止覆盖
                short[] slice_copy = new short[slice.length];
                System.arraycopy(slice,0,slice_copy,0,slice.length);

                pcmDataArr.add(slice_copy);
                final boolean vad_status = vad.process(16000,slice_copy,false);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        lb_vad_status.setText(vad_status ? "有声":"无声");
                    }
                });
            }
        });

    }


    public void onClick_originPlay(View view) {
        playAudio(new IBerforePlayAudio() {
            @Override
            public short[] onBerforePlayAudio(short[] pcm) {
                return pcm;
            }
        });
    }

    public void onClick_nsPlaye(View view) {
        playAudio(new IBerforePlayAudio() {
            @Override
            public short[] onBerforePlayAudio(short[] pcm) {
                return  ns.process(pcm,PCM_SLICE_MS);
            }
        });
    }

    int micLevelIn = 0;
    public void onClick_agcPlay(View view) {
        playAudio(new IBerforePlayAudio() {
            @Override
            public short[] onBerforePlayAudio(short[] pcm) {
                WebRtcAgc.ResultOfProcess ret = agc.process(pcm,pcm.length,micLevelIn,0);
                if (ret.ret != 0){
                    Log.e("TAG","agc.process faield!");
                    return pcm;
                }
                if (ret.saturationWarning == 1){
                    Log.e("TAG","agc.process saturationWarning == 1");
                }
                micLevelIn = ret.outMicLevel;
                return ret.out;
            }
        });
    }

    public void onClick_agcNSPlay(View view) {
        playAudio(new IBerforePlayAudio() {
            @Override
            public short[] onBerforePlayAudio(short[] pcm) {
                WebRtcAgc.ResultOfProcess ret = agc.process(pcm,pcm.length,micLevelIn,0);
                if (ret.ret != 0){
                    Log.e("TAG","agc.process faield!");
                    return pcm;
                }
                if (ret.saturationWarning == 1){
                    Log.e("TAG","agc.process saturationWarning == 1");
                }
                micLevelIn = ret.outMicLevel;
                return ns.process(ret.out,PCM_SLICE_MS);
            }
        });
    }

    public void onClick_nsAgcPlay(View view) {
        playAudio(new IBerforePlayAudio() {
            @Override
            public short[] onBerforePlayAudio(short[] pcm) {
                pcm = ns.process(pcm,PCM_SLICE_MS);
                WebRtcAgc.ResultOfProcess ret = agc.process(pcm,pcm.length,micLevelIn,0);
                if (ret.ret != 0){
                    Log.e("TAG","agc.process faield!");
                    return pcm;
                }
                if (ret.saturationWarning == 1){
                    Log.e("TAG","agc.process saturationWarning == 1");
                }
                micLevelIn = ret.outMicLevel;
                return ret.out;
            }
        });
    }

    public void onClick_rec_play(View view) {
        setEnable(false);
        audioCapturer.setOnAudioCapturedListener(new AudioCapturer.OnAudioCapturedListener() {
            @Override
            public void onAudioCaptured(short[] audioData, int stamp) {
                bufferSlice.input(audioData, audioData.length, stamp, audioData.length * 1000 / 16000, new BufferSlice.ISliceOutput() {
                    @Override
                    public void onOutput(short[] slice, int stamp) {
                        final short [] nearendNoisy = new short[slice.length];
                        System.arraycopy(slice,0,nearendNoisy,0,slice.length);

                        taskQuenu.async(new TaskQuenu.Task() {
                            @Override
                            public void run() throws TaskQuenu.ExitInterruptedException {
                                short[] nearendClean = ns.process(nearendNoisy,PCM_SLICE_MS);
                                short[] aecm_out = aecm.process(nearendNoisy,nearendClean,nearendNoisy.length, audioCapturer.getRecordDelayMS() + audioPlayer.getPlayDelayMS());
                                if(aecm_out == null){
                                    aecm_out = nearendClean;
                                    Log.e("TAG","aecm.process return null");
                                }
                                aecm.bufferFarend(aecm_out,aecm_out.length);
                                audioPlayer.play(aecm_out,0,aecm_out.length);
                            }
                        });
                    }
                });
            }
        });
        audioCapturer.startCapture();
        audioPlayer.startPlayer();
    }


    private void playAudio(final IBerforePlayAudio cb){
        sw_record.setChecked(false);
        audioCapturer.stopCapture();
        taskQuenu.async(new TaskQuenu.Task() {
            @Override
            public void run() throws TaskQuenu.ExitInterruptedException {
                setEnable(false);
                audioPlayer.startPlayer();
                interrupted = false;
                micLevelIn = 0;
                for (short[] pcm : pcmDataArr){
                    short[] pcm_after = cb.onBerforePlayAudio(pcm);
                    if(pcm_after != null){
                        audioPlayer.play(pcm_after,0,pcm_after.length);
                    }
                    if (interrupted){
                        break;
                    }
                }
                audioPlayer.stopPlayer();
                setEnable(true);
            }
        });
    }



    private interface IBerforePlayAudio
    {
       short[] onBerforePlayAudio(short[] pcm);
    }

    private void setEnable(final boolean enable){
        handler.post(new Runnable() {
            @Override
            public void run() {
                bt_agc.setEnabled(enable);
                bt_agc_ns.setEnabled(enable);
                bt_ns_agc.setEnabled(enable);
                bt_ns.setEnabled(enable);
                bt_origin.setEnabled(enable);
                sw_record.setEnabled(enable);
            }
        });
    }

    Ticker backTicker = new Ticker();
    @Override
    public void onBackPressed() {
        if(backTicker.elapsedTime() > 2*1000){
            Toast.makeText(this,"两秒内连续点击返回键退出程序", Toast.LENGTH_LONG).show();
            backTicker.resetTime();
        }else {
            super.onBackPressed();
        }
        interrupted = true;
    }
}
