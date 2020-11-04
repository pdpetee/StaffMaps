package com.kamzs.staffmaps;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.GeoPoint;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mumayank.com.airlocationlibrary.AirLocation;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Location myLocation;
    private LatLng myLocationCoordinates;
    private MarkerOptions myLocationMarkerOptions;
    private Button recenterButton, getDirectionsButton;
    private RequestQueue mQueue;
    private List<List<HashMap<String,String>>> decodedPolylineList;
    private DocumentReference documentReferenceStaff = FirebaseFirestore.getInstance()
            .collection("Appointment").document("StaffDetails");
    private DocumentReference documentReferenceUser = FirebaseFirestore.getInstance()
            .collection("Appointment").document("CustomerDetails");
    
    //Placeholder coordinates
    LatLng myDestinationCoordinates;

    AirLocation mAirLocation = new AirLocation(this, new AirLocation.Callback() {
        @Override
        public void onSuccess(ArrayList<Location> arrayList) {
            Log.d("AirLocation", "onSucess. Location details: " + arrayList);
            myLocation = new Location(arrayList.get(0));
            if (!new LatLng(myLocation.getLatitude(), myLocation.getLongitude()).equals(myLocationCoordinates)){
                myLocationCoordinates = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
                myLocationMarkerOptions = new MarkerOptions().position(myLocationCoordinates).title("Your Location");
                saveCoordinates(myLocationCoordinates);
            }
        }

        @Override
        public void onFailure(AirLocation.LocationFailedEnum locationFailedEnum) {
            Log.d("AirLocation", "onFailure: " + locationFailedEnum);
        }
    }, false,
            2000,
            "Please enable location permissions for this app to work.");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mAirLocation.start();
        getDirectionsButton = findViewById(R.id.get_directions_button);
        recenterButton = findViewById(R.id.recenter_button);
        recenterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocationCoordinates, 10));
                Log.d("EnterButton", "Your location latlng is: " + myLocationCoordinates);
            }
        });
        recenterButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myDestinationCoordinates, 10));
                return false;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mAirLocation.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mAirLocation.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setTrafficEnabled(true);
        mQueue = Volley.newRequestQueue(this);
        decodedPolylineList = new ArrayList<>();
        fetchCustomerLocation();
    }

    public String getRouteURL (LatLng origin, LatLng destination, String apiKey){
//        String originText = "5.331130,100.266470";  //Placeholder URL
        String originText = origin.latitude+","+origin.longitude;
        String destinationText = destination.latitude+","+destination.longitude;
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" + originText + "&destination=" + destinationText + "&key=" + apiKey;
        return url;
    }

    public void saveCoordinates(LatLng myLocationCoordinates){
        if (myLocationCoordinates == null){
            return;
        }
        Map<String, GeoPoint> dataToSave = new HashMap<String, GeoPoint>();
        GeoPoint geoPoint = new GeoPoint(myLocationCoordinates.latitude, myLocationCoordinates.longitude);
        dataToSave.put("LatLng", geoPoint);
        Log.d("saveCoordinates", "Latitude, Longitude = " + dataToSave);

        documentReferenceStaff.set(dataToSave).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("saveCoordinates", "onSuccess: Document successfully saved");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("saveCoordinates", "onFailure");
                e.printStackTrace();
            }
        });
    }

    public void fetchCustomerLocation (){
        documentReferenceUser.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                GeoPoint customerLocationGeopoint = value.getGeoPoint("LatLng");
                if (customerLocationGeopoint != null){
                    myDestinationCoordinates = new LatLng(customerLocationGeopoint.getLatitude(), customerLocationGeopoint.getLongitude());
                    Log.d("fetchCustomerLocation", "onEvent: Location retrieval successful: " + value);
                    getDirectionsButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            getUrlAndDrawPolyline();
                        }
                    });
                }
                else{
                    getDirectionsButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Toast.makeText(MapsActivity.this, "Failed to retrieve customer location", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    public void getUrlAndDrawPolyline(){

        ////add some code or a listener to only execute once the value of myLocationCoordinates have been updated by AirLocation
        Log.d("getUrlAndDrawPolyline", "Value of myLocationCoordinates: " + myLocationCoordinates);
        String url = getRouteURL(myLocationCoordinates, myDestinationCoordinates, getString(R.string.google_maps_key));
        Log.d("onSuccess.getRouteURL", "Your url is " + url);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.d("onResponse", "Starting");
                DirectionsJSONParser djp = new DirectionsJSONParser();
                decodedPolylineList = djp.parse(response);
                Log.d("onResponse", "JSON Response is: " + response);
                Log.d("onResponse", "decodedPolylineList: " + decodedPolylineList);
                    PolylineOptions polylineOptions = new PolylineOptions();
                    ArrayList<LatLng> polyList = new ArrayList<>();
                    for (List<HashMap<String,String>> i : decodedPolylineList){
                        for (HashMap<String,String> j: i){
                            double latitude = Double.parseDouble(j.get("lat"));
                            double longitude = Double.parseDouble(j.get("lng"));
                            LatLng position = new LatLng(latitude, longitude);
                            polyList.add(position);
                        }
                    }
                    mMap.clear();
                    LatLng myLocationLatLng = new LatLng(polyList.get(0).latitude, polyList.get(0).longitude);
                    mMap.addMarker(new MarkerOptions().position(polyList.get(0)).title("Your location"));
                    mMap.addMarker(new MarkerOptions().position(polyList.get(polyList.size()-1)).title("Your destination"));
                    Log.d("onResponse", "Markers added");

                    polylineOptions.addAll(polyList);
                    polylineOptions.width(15);
                    polylineOptions.color(Color.BLUE);
                    mMap.addPolyline(polylineOptions);
                    Log.d("onResponse", "Polyline added");

                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocationLatLng, 16));
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
            }
        });
        mQueue.add(jsonObjectRequest);
    }
}