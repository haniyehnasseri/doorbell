package com.example.doorbell;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;


import com.androidnetworking.AndroidNetworking;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpGet;
import com.koushikdutta.async.http.AsyncHttpPut;
import com.koushikdutta.async.http.AsyncHttpResponse;
import com.koushikdutta.async.http.callback.HttpConnectCallback;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

    private static String SERVER_ADDRESS = "http://103.215.221.170";

    private final LocationListener mLocationListener;
    public LocationManager mLocationManager;
    private Handler locationHandler = new Handler();
    private Timer locationTimer = null;
    private Handler wifiDataHandler = new Handler();
    private Timer wifiDataTimer = null;

    public final long notify_interval = 2000;
    public final long wifi_get_data_interval = 4000;


    private String deviceName = null;
    private String deviceAddress;
    public static Handler handler;
    public static BluetoothSocket mmSocket;
    public static ConnectedThread connectedThread;
    public static CreateConnectThread createConnectThread;


    public String wifiImage = "";
    public boolean newWifiData = false;

    public Location lastLocation = null;

    public Location boardLocation = new Location("board");

    public boolean close = true;

    public float distance = -1;


    private final static int CONNECTING_STATUS = 1; // used in bluetooth handler to identify message status
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update

    public String whichButton = "";

    public MainActivity() {

        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                System.out.println(location.getLatitude() + "----->" + location.getLongitude());
                lastLocation = location;
                //storeDistanceOnServer();
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                tryEnableLocation();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }
        };

    }

    private void tryEnableLocation() {
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Notify user
            final Context context = this;
            new AlertDialog.Builder(this)
                    .setMessage("Your location service is disabled; Do you want to enable it now?")
                    .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                            context.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void storeDistanceOnServer() {

        close = false;
        distance = boardLocation.distanceTo(lastLocation);

        if(distance <= 10.0){
            close = true;
        }

        AsyncHttpPut put = new AsyncHttpPut(SERVER_ADDRESS + "/location?close=" + close);
        AsyncHttpClient.getDefaultInstance().execute(put, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }
                System.out.println("Server says: " + response.code());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        bb.recycle();
                    }
                });
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Api Access
        AndroidNetworking.initialize(getApplicationContext());

        // UI Initialization
        final Button buttonConnect = findViewById(R.id.buttonConnect);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        final Button buttonToggle = findViewById(R.id.buttonToggle);

        initializeLocationService();

        boardLocation.setLatitude(35.725289177);
        boardLocation.setLongitude(51.489205847);

        // new
        initializeToggleButton();





        ///
        class TimerTaskToGetWifiData extends TimerTask {

            boolean newImageExists = false;
            ImageView imageView = findViewById(R.id.imageView);

            /*public TimerTaskToGetWifiData() {
                imageLoader.init(ImageLoaderConfiguration.createDefault(getApplicationContext()));
                imageLoader.displayImage(SERVER_ADDRESS + "/image", imageView);
            }*/

            @Override
            public void run() {
                wifiDataHandler.post(new Runnable() {
                    @Override
                    public void run() {


                        checkNewImageExists();

                        if (newImageExists) {
                            getImage();
                            System.out.println("before : " + newImageExists);
                            System.out.println(wifiImage);
                            if(wifiImage.length() > 0){
                                String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                                showAlert("Wifi : Someone Arrived ! " + currentDateandTime );
                                String base64String = wifiImage.substring(2);
                                byte[] decodedString = Base64.decode(base64String, Base64.DEFAULT);
                                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                                imageView.setImageBitmap(decodedByte);
                                unsetNewImageExists();
                                System.out.println(wifiImage);
                                System.out.println("after : " + newImageExists);
                            }
                        }
                    }
                });
            }

            private void unsetNewImageExists() {
                newImageExists = false;
                wifiImage = "";
                AsyncHttpPut put = new AsyncHttpPut(SERVER_ADDRESS + "/available?flag=false");
                AsyncHttpClient.getDefaultInstance().execute(put, new HttpConnectCallback() {
                    @Override
                    public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                        if (ex != null) {
                            ex.printStackTrace();
                            return;
                        }
                        response.setDataCallback(new DataCallback() {
                            @Override
                            public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                                bb.recycle();
                            }
                        });
                    }
                });
            }

            private void checkNewImageExists() {
                AsyncHttpGet get = new AsyncHttpGet(SERVER_ADDRESS + "/available");
                AsyncHttpClient.getDefaultInstance().executeString(get, new AsyncHttpClient.StringCallback() {
                    @Override
                    public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                        if (source.code() == 200 && result != null) {
                            if (result.equals("false") || result.equals("no") || result.equals("0"))
                                newImageExists = false;
                            else
                                newImageExists = true;
                            System.out.println("check available " + newImageExists);
                        }
                    }
                });
            }
        }

        wifiDataTimer = new Timer();
        wifiDataTimer.schedule(new TimerTaskToGetWifiData(), 0, wifi_get_data_interval);
        ///

        // If a bluetooth device has been selected from SelectDeviceActivity
        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName != null) {
            // Get the device address to make BT Connection
            deviceAddress = getIntent().getStringExtra("deviceAddress");
            toolbar.setSubtitle("Connecting to " + deviceName + "...");
            buttonConnect.setEnabled(false);

            /*
            This is the most important piece of code. When "deviceName" is found
            the code will call a new thread to create a bluetooth connection to the
            selected device (see the thread code below)
             */
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            createConnectThread = new CreateConnectThread(bluetoothAdapter, deviceAddress);
            createConnectThread.start();
        }

        /*
        Second most important piece of Code. GUI Handler
         */
       handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CONNECTING_STATUS:
                        switch (msg.arg1) {
                            case 1:
                                toolbar.setSubtitle("Connected to " + deviceName);
                                buttonConnect.setEnabled(true);
                                break;
                            case -1:
                                toolbar.setSubtitle("Device fails to connect");
                                buttonConnect.setEnabled(true);
                                break;
                            case -2:
                                toolbar.setSubtitle("mmSocket is null " + deviceName + "//" + deviceAddress);
                                 buttonConnect.setEnabled(true);
                                break;
                        }
                        break;

                    case MESSAGE_READ:
                        String arduinoMsg = msg.obj.toString(); // Read message from Arduino


                        if(!arduinoMsg.isEmpty())
                            connectedThread.write("bluetooth ack");
                        String currentDateandTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                        showAlert(arduinoMsg + currentDateandTime );
                        break;
                }
            }
        };

        // Select Bluetooth Device
        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Move to adapter list
                Intent intent = new Intent(MainActivity.this, SelectDeviceActivity.class);
                startActivity(intent);
            }
        });

        // Button to ON/OFF LED on Arduino Board
        buttonToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                EditText editText = (EditText) findViewById(R.id.message);
                String message = editText.getText().toString();

//                if(distance != -1){
//                    showAlert("distance : " + String.valueOf(distance));
//                }
//                if(lastLocation != null){
//                    showAlert("location : " + String.valueOf(lastLocation.getLatitude()) + " : " +
//                            String.valueOf(lastLocation.getLongitude()));
//                }

                if(whichButton.equals("Bluetooth")){
                    close = true;
                }
                else if(whichButton.equals("Wifi")){
                    close = false;
                }


                if(close){
                    connectedThread.write(message);
                }
                else{
                    storeLCDWifi(message);
                }


            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initializeLocationService() {
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION}, 123);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        tryEnableLocation();

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, (float) 0.1, mLocationListener);
    }

    /* ============================ Thread to Create Bluetooth Connection =================================== */
    public static class CreateConnectThread extends Thread {

        public CreateConnectThread(BluetoothAdapter bluetoothAdapter, String address) {
            /*
            Use a temporary object that is later assigned to mmSocket
            because mmSocket is final.
             */
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothSocket tmp = null;
            UUID uuid = bluetoothDevice.getUuids()[0].getUuid();

            try {
                /*
                Get a BluetoothSocket to connect with the given BluetoothDevice.
                Due to Android device varieties,the method below may not work fo different devices.
                You should try using other methods i.e. :
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                 */
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));

            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.cancelDiscovery();
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                if (mmSocket == null) {
                    handler.obtainMessage(CONNECTING_STATUS, -2, -1).sendToTarget();
                }
                Log.e("Status", "Device connected");
                handler.obtainMessage(CONNECTING_STATUS, 1, -1).sendToTarget();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                    Log.e("Status", "Cannot connect to device");
                    handler.obtainMessage(CONNECTING_STATUS, -1, -1).sendToTarget();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            connectedThread = new ConnectedThread(mmSocket);
            connectedThread.run();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    /* =============================== Thread for Data Transfer =========================================== */
    public static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes = 0; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    /*
                    Read from the InputStream from Arduino until termination character is reached.
                    Then send the whole String message to GUI Handler.
                     */
                    buffer[bytes] = (byte) mmInStream.read();
                    String readMessage;
                    if (buffer[bytes] == '\n') {
                        readMessage = new String(buffer, 0, bytes);
                        Log.e("Arduino Message", readMessage);
                        handler.obtainMessage(MESSAGE_READ, readMessage).sendToTarget();
                        bytes = 0;
                    } else {
                        bytes++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes(); //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("Send Error", "Unable to send message", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    /* ============================ Terminate Connection at BackPress ====================== */
    @Override
    public void onBackPressed() {
        // Terminate Bluetooth Connection and close app
        if (createConnectThread != null) {
            createConnectThread.cancel();
        }
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }



    public void showAlert(String message){
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("New message From Doorbell ! ")
                .setMessage(message)

                // Specifying a listener allows you to take an action before dismissing the dialog.
                // The dialog is automatically dismissed when a dialog button is clicked.
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Continue with delete operation
                    }
                })

                // A null listener allows the button to dismiss the dialog and take no further action.
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public void getImage() {
        // time
        AsyncHttpGet get = new AsyncHttpGet(SERVER_ADDRESS + "/bmage");
        AsyncHttpClient.getDefaultInstance().executeString(get, new AsyncHttpClient.StringCallback() {
            @Override
            public void onCompleted(Exception e, AsyncHttpResponse source, String result) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }
                wifiImage = result;
            }
        });
    }

    public void storeLCDWifi(String lcdMessage){
        AsyncHttpPut put = new AsyncHttpPut(SERVER_ADDRESS + "/message?text=" + lcdMessage);
        AsyncHttpClient.getDefaultInstance().execute(put, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }
                System.out.println("Server says: " + response.code());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        bb.recycle();
                    }
                });
            }
        });
    }

    // def : wifi
    public void toggleCloseClick(View v){
        Button closeButton = (Button) findViewById(R.id.closeButton);
        String buttonText = (String) closeButton.getText();
        if(buttonText.equals("Bluetooth")){
            closeButton.setText("Wifi");
            whichButton = "Bluetooth";
            setClose("true");
        }
        else if(buttonText.equals("Wifi")){
            closeButton.setText("Bluetooth");
            whichButton = "Wifi";
            setClose("false");
        }
    }



    public void setClose(String close){
        AsyncHttpPut put = new AsyncHttpPut(SERVER_ADDRESS + "/location?close=" + close);
        AsyncHttpClient.getDefaultInstance().execute(put, new HttpConnectCallback() {
            @Override
            public void onConnectCompleted(Exception ex, AsyncHttpResponse response) {
                if (ex != null) {
                    ex.printStackTrace();
                    return;
                }
                System.out.println("Server says: " + response.code());
                response.setDataCallback(new DataCallback() {
                    @Override
                    public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                        bb.recycle();
                    }
                });
            }
        });
    }

    public  void initializeToggleButton(){
        whichButton = "Wifi";
        setClose("false");
    }
}