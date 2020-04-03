package com.example.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.baidu.mapapi.model.LatLng;
import com.example.log4j.LogUtil;
import com.example.mockgps.MainActivity;
import com.example.mockgps.R;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.UUID;

public class MockGpsService extends Service {


    private String TAG = "MockGpsService";

    private LocationManager locationManager;
    private HandlerThread handlerThread;
    private Handler handler;

    private boolean isStop = true;

    //经纬度字符串
    private String latLngInfo = "104.06121778639009&30.544111926165282";

    //悬浮窗
    private FloatWindow floatWindow;
    private boolean isFloatWindowStart = false;


    public static final int RunCode = 0x01;
    public static final int StopCode = 0x02;

    //log debug
    private static Logger log = Logger.getLogger(MockGpsService.class);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onCreate() {
        LogUtil.configLog();
        Log.d(TAG, "onCreate");
        log.debug(TAG + ": onCreate");
        super.onCreate();
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        //for test
        getProviders();
        //remove default network icon provider
        rmNetworkTestProvider();
        //remove gps provider
        rmGPSTestProvider();

        //add a new test network icon provider
        setNetworkTestProvider();
//        add a GPS test Provider
        setGPSTestProvider();

        //thread
        handlerThread = new HandlerThread(getUUID(), -2);
        handlerThread.start();

        handler = new Handler(handlerThread.getLooper()) {
            public void handleMessage(Message msg) {
                try {
                    Thread.sleep(128);
                    if (!isStop) {

                        //remove default network icon provider
                        //rmNetworkProvider();

                        //add a new network icon provider
                        //setNewNetworkProvider();

                        setTestProviderLocation();
                        setGPSLocation();
                        sendEmptyMessage(0);
                        //broadcast to MainActivity
                        Intent intent = new Intent();
                        intent.putExtra("statusCode", RunCode);
                        intent.setAction("com.example.service.MockGpsService");
                        sendBroadcast(intent);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.d(TAG, "handleMessage error");
                    log.debug(TAG + ": handleMessage error");
                    Thread.currentThread().interrupt();
                }
            }
        };
        handler.sendEmptyMessage(0);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d(TAG, "onStart");
        log.debug(TAG + ": onStart");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        log.debug(TAG + ": onStartCommand");
//        DisplayToast("Mock Location Service Start");
        //

        String channelId = "channel_01";
        String name = "channel_name";
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = null;

        //click通知跳转
        Intent it = new Intent(this, MainActivity.class);
        it.addCategory(Intent.CATEGORY_LAUNCHER);
        it.setAction(Intent.ACTION_MAIN);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
            Log.i(TAG, mChannel.toString());
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(mChannel);
            }
            notification = new Notification.Builder(this)
                    .setChannelId(channelId)
                    .setContentTitle("位置模拟服务已启动")
                    .setContentText("MockLocation service is running")
                    .setContentIntent(pIntent)
                    .setSmallIcon(R.mipmap.icon).build();
        } else {
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle("位置模拟服务已启动")
                    .setContentText("MockLocation service is running")
                    .setSmallIcon(R.mipmap.icon)
                    .setContentIntent(pIntent)
                    .setOngoing(true)
                    .setChannelId(channelId);//无效
            notification = notificationBuilder.build();
        }
        startForeground(1, notification);
        //

        //get icon info from mainActivity
        latLngInfo = intent.getStringExtra("key");
        Log.d(TAG, "DataFromMain is " + latLngInfo);
        log.debug(TAG + ": DataFromMain is " + latLngInfo);
        //start to refresh icon
        isStop = false;

        //这里开启悬浮窗
        if (!isFloatWindowStart) {
            floatWindow = new FloatWindow(this);
            floatWindow.showFloatWindow();
            isFloatWindowStart = true;
        }


//        return START_STICKY;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        log.debug(TAG + ": onDestroy");
//        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show();

//        DisplayToast("Mock Loction Service finish");
        isStop = true;

        //移除悬浮窗
        floatWindow.hideFloatWindow();
        isFloatWindowStart = false;

        handler.removeMessages(0);
        handlerThread.quit();

        //remove test provider
        rmNetworkTestProvider();
        rmGPSTestProvider();

        //rmGPSProvider();
        stopForeground(true);

        //broadcast to MainActivity
        Intent intent = new Intent();
        intent.putExtra("statusCode", StopCode);
        intent.setAction("com.example.service.MockGpsService");
        sendBroadcast(intent);

        super.onDestroy();
    }

    //provider test
    public void getProviders() {
        List<String> providerList = locationManager.getProviders(true);
        for (String str : providerList) {
            Log.d("PROV", str);
            log.debug("active provider: " + str);
        }
    }

    //generate a icon
    public Location generateLocation(LatLng latLng) {
        Location loc = new Location("gps");


        loc.setAccuracy(2.0F);
        loc.setAltitude(55.0D);
        loc.setBearing(1.0F);
        Bundle bundle = new Bundle();
        bundle.putInt("satellites", 7);
        loc.setExtras(bundle);


        loc.setLatitude(latLng.latitude);
        loc.setLongitude(latLng.longitude);
//        loc.setAccuracy(1.0F);
//        loc.setAltitude(10);
//        loc.setBearing(90);
        loc.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 17) {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
//        Log.d("WATCH",loc.toString());
        return loc;
    }

    //给test provider添加网络定位
    private void setTestProviderLocation() {
        //default icon 30.5437233 104.0610342 成都长虹科技大厦
        Log.d(TAG, "setNetworkLocation: " + latLngInfo);
        log.debug(TAG + ": setNetworkLocation: " + latLngInfo);
        String[] latLngStr = latLngInfo.split("&");
        LatLng latLng = new LatLng(Double.valueOf(latLngStr[1]), Double.valueOf(latLngStr[0]));
        String providerStr = LocationManager.NETWORK_PROVIDER;
//        String providerStr2 = LocationManager.GPS_PROVIDER;
        try {
            locationManager.setTestProviderLocation(providerStr, generateLocation(latLng));
//            locationManager.setTestProviderLocation(providerStr2, generateLocation(latLng));
            //for test
//            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, generateLocation(latLng));
        } catch (Exception e) {
            Log.d(TAG, "setNetworkLocation error");
            log.debug(TAG + ": setNetworkLocation error");
            e.printStackTrace();
        }
    }

    //set gps icon
    private void setGPSLocation(){
        //default icon 30.5437233 104.0610342 成都长虹科技大厦
        Log.d(TAG, "setGPSLocation: " + latLngInfo);
        log.debug(TAG + ": setGPSLocation: " + latLngInfo);
        String[] latLngStr = latLngInfo.split("&");
        LatLng latLng = new LatLng(Double.valueOf(latLngStr[1]), Double.valueOf(latLngStr[0]));
        String providerStr = LocationManager.GPS_PROVIDER;
//        String providerStr2 = LocationManager.GPS_PROVIDER;
        try {
            locationManager.setTestProviderLocation(providerStr, generateLocation(latLng));
//            locationManager.setTestProviderLocation(providerStr2, generateLocation(latLng));
            //for test
//            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, generateLocation(latLng));
        } catch (Exception e) {
            Log.d(TAG, "setGPSLocation error");
            log.debug(TAG + ": setGPSLocation error");
            e.printStackTrace();
        }
    }

    //remove network provider
    private void rmNetworkTestProvider() {
        try {
            String providerStr = LocationManager.NETWORK_PROVIDER;
            if (locationManager.isProviderEnabled(providerStr)) {
                Log.d(TAG, "now remove NetworkProvider");
                log.debug(TAG + ": now remove NetworkProvider");
//                locationManager.setTestProviderEnabled(providerStr,true);
                locationManager.removeTestProvider(providerStr);
            } else {
                Log.d(TAG, "NetworkProvider is not enabled");
                log.debug(TAG + ": NetworkProvider is not enabled");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "rmNetworkProvider error");
            log.debug(TAG + ": rmNetworkProvider error");
        }
    }

    //set new network provider
    private void setNetworkTestProvider() {
        String providerStr = LocationManager.NETWORK_PROVIDER;
//        String providerStr = LocationManager.GPS_PROVIDER;
        try {
            locationManager.addTestProvider(providerStr, false, false,
                    false, false, false, false,
                    false, 1, Criteria.ACCURACY_FINE);
            Log.d(TAG, "addTestProvider[NETWORK_PROVIDER] success");
            log.debug(TAG + ": addTestProvider[NETWORK_PROVIDER] success");
//            locationManager.setTestProviderStatus("network", LocationProvider.AVAILABLE, null,
//                    System.currentTimeMillis());
        } catch (SecurityException e) {
            e.printStackTrace();
            Log.d(TAG, "addTestProvider[NETWORK_PROVIDER] error");
            log.debug(TAG + ": addTestProvider[NETWORK_PROVIDER] error");
        }
        if (!locationManager.isProviderEnabled(providerStr)) {
            try {
                locationManager.setTestProviderEnabled(providerStr, true);
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "setTestProviderEnabled[NETWORK_PROVIDER] error");
                log.debug(TAG + ": setTestProviderEnabled[NETWORK_PROVIDER] error");
            }
        }
    }

    // for test: set GPS provider
    private void rmGPSTestProvider() {
        try {
            String providerStr = LocationManager.GPS_PROVIDER;
            if (locationManager.isProviderEnabled(providerStr)) {
                Log.d(TAG, "now remove GPSProvider");
                log.debug(TAG + ": now remove GPSProvider");
//                locationManager.setTestProviderEnabled(providerStr,true);
                locationManager.removeTestProvider(providerStr);
            } else {
                Log.d(TAG, "GPSProvider is not enabled");
                log.debug(TAG + ": GPSProvider is not enabled");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "rmGPSProvider error");
            log.debug(TAG + ": rmGPSProvider error");
        }
    }

    private void setGPSTestProvider() {
        LocationProvider provider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        try {
            locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, true,
                    false, true, true, true, 0, 5);
            Log.d(TAG, "addTestProvider[GPS_PROVIDER] success");
            log.debug(TAG + ": addTestProvider[GPS_PROVIDER] success");
        }catch (Exception e){
            e.printStackTrace();
            Log.d(TAG, "addTestProvider[GPS_PROVIDER] error");
            log.debug(TAG + ": addTestProvider[GPS_PROVIDER] error");
        }

//        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }catch (Exception e){
                e.printStackTrace();
                Log.d(TAG, "setTestProviderEnabled[GPS_PROVIDER] error");
                log.debug(TAG + ": setTestProviderEnabled[GPS_PROVIDER] error");
            }

        }


        //新
        locationManager.setTestProviderStatus(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null,
                System.currentTimeMillis());
    }


    //uuid random
    public static String getUUID() {
        return UUID.randomUUID().toString();
    }


    //get service
    public class ServiceBinder extends Binder {
        public MockGpsService getService() {
            return MockGpsService.this;
        }
    }

    public void DisplayToast(String str) {
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 220);
        toast.show();
    }

}


