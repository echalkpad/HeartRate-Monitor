package com.motion.lab.pulse;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.motion.lab.pulse.adapter.DeviceViewAdapter;
import com.motion.lab.pulse.model.PulseDevice;
import com.motion.lab.pulse.network.MqttHandler;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.ArrayList;
import java.util.Random;

import butterknife.BindView;
import butterknife.ButterKnife;

public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private ArrayList<PulseDevice> deviceList = new ArrayList<>();

    @BindView(R.id.device_list_view) RecyclerView recyclerView;
    private MqttHandler mqttHandler;
    private DeviceViewAdapter deviceViewAdapter;
    @BindView(R.id.fab) FloatingActionButton fab;
    private EditText input;

    private String clientId;

    IconicsDrawable plusIcon;
    void setupIcon(){
        plusIcon = new IconicsDrawable(HomeActivity.this).
                icon(GoogleMaterial.Icon.gmd_add)
                .color(getResources().getColor(R.color.cardview_light_background))
                .sizeDp(24);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        ButterKnife.bind(this);

        if (!AppConfig.isLoggedIn(HomeActivity.this)){
            AppConfig.movePageAndFinish(HomeActivity.this, LoginActivity.class);
        }else {
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            setupIcon();

            if (fab != null) {
                fab.setImageDrawable(plusIcon);
                fab.setOnClickListener(addButtonClickListener);
            }

            setUpRecyclerView();

            try {
                mqttHandler = MqttHandler.GetInstance(HomeActivity.this);
            }catch (MqttException e){
                e.printStackTrace();
                Log.e(TAG, "onCreate: "+e.getMessage());
            }
        }
    }

    // region menu selection
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_sign_out) {
            AppConfig.movePageAndFinish(HomeActivity.this, LoginActivity.class);
            AppConfig.saveLoggedStatus(HomeActivity.this, AppConfig.LOGGED_OUT);
            // mqttHandler.disconnect();
            MqttHandler.RemoveInstance();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    // endregion

    // region activity button listener
    View.OnClickListener addButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            AlertDialog.Builder builder = new AlertDialog.Builder(HomeActivity.this);
            input = new EditText(HomeActivity.this);
            builder.setView(input);
            builder.setTitle("Add device id");
            // Set up the buttons
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            builder.setPositiveButton("OK", addDeviceId);
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        }
    };

    DialogInterface.OnClickListener addDeviceId = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            mqttHandler.subscribe(input.getText().toString(), MqttHandler.QOS.QOS_AT_LEAST_ONCE);
            PulseDevice aDevice = new PulseDevice().setId(input.getText().toString())
                    .setName(input.getText().toString())
                    .setHeartBeat(new Random().nextInt(100))
                    .createMessageListener();
            deviceList.add(aDevice);
            mqttHandler.registerListener(aDevice.getMessageListener());

            Snackbar.make(fab, "Device added", Snackbar.LENGTH_LONG)
                    .setAction("OK", null).show();
            deviceViewAdapter.notifyDataSetChanged();
            hideOrShowEmpty();
        }
    };
    // endregion

    void setUpRecyclerView(){
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(HomeActivity.this);
        recyclerView.setLayoutManager(mLayoutManager);

        deviceViewAdapter = new DeviceViewAdapter(HomeActivity.this, deviceList);
        recyclerView.setAdapter(deviceViewAdapter);
        RecyclerTouchListener touchListener = new RecyclerTouchListener(HomeActivity.this,
                recyclerView, recyclerOnItemClick);
        recyclerView.addOnItemTouchListener(touchListener);
        hideOrShowEmpty();
    }

    // region Recycler
    PulseDevice device;
    ClickListener recyclerOnItemClick = new ClickListener() {
        @Override
        public void onClick(View view, int position) {
            device = deviceList.get(position);

            Log.i(TAG, "isViewHolder exist: "+ (deviceList.get(position).getViewHolder()==null));

            View res = view.findViewById(R.id.device_quick_result);
            res.setVisibility(res.getVisibility() == View.VISIBLE ? View.GONE:View.VISIBLE);

            view.findViewById(R.id.device_edit_button).setOnClickListener(editClickListener);
        }

        @Override
        public void onLongClick(View view, int position) {

        }
    };

    View.OnClickListener editClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Bundle bundle = new Bundle();
            bundle.putString("id", device.getId());
            bundle.putString("name", device.getName());
            AppConfig.movePage(HomeActivity.this, DetailActivity.class, bundle);
        }
    };

    void hideOrShowEmpty(){
        if (deviceList.size()>0)
            (findViewById(R.id.empty_list_view)).setVisibility(View.GONE);
        else
            (findViewById(R.id.empty_list_view)).setVisibility(View.VISIBLE);
    }

    public interface ClickListener {
        void onClick(View view, int position);
        void onLongClick(View view, int position);
    }

    public static class RecyclerTouchListener implements RecyclerView.OnItemTouchListener {

        private GestureDetector gestureDetector;
        private HomeActivity.ClickListener clickListener;

        public RecyclerTouchListener(Context context, final RecyclerView recyclerView, final HomeActivity.ClickListener clickListener) {
            this.clickListener = clickListener;
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null && clickListener != null) {
                        clickListener.onLongClick(child, recyclerView.getChildPosition(child));
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {

            View child = rv.findChildViewUnder(e.getX(), e.getY());
            if (child != null && clickListener != null && gestureDetector.onTouchEvent(e)) {
                clickListener.onClick(child, rv.getChildPosition(child));
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

        }
    }
    // endregion
}
