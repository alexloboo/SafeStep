package com.equipo5.safestep.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.equipo5.safestep.R
import com.equipo5.safestep.fragments.CrimeFormFragment
import com.equipo5.safestep.models.User
import com.equipo5.safestep.network.AuthService
import com.equipo5.safestep.network.Callback
import com.equipo5.safestep.network.FirestoreService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.geocoding.v5.GeocodingCriteria
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.core.exceptions.ServicesException
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.places.autocomplete.ui.PlaceAutocompleteFragment
import com.mapbox.mapboxsdk.plugins.places.autocomplete.ui.PlaceSelectionListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Response
import timber.log.Timber
import java.lang.ref.WeakReference


const val DEFAULT_INTERVAL_IN_MILLISECONDS = 1000L
const val DEFAULT_MAX_WAIT_TIME = DEFAULT_INTERVAL_IN_MILLISECONDS * 5

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    OnMapReadyCallback, PermissionsListener {

    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapView: MapView
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var locationEngine: LocationEngine
    private val callback = LocationChangeListeningActivityLocationCallback(this)

    private val firestoreService = FirestoreService()
    private val authService = AuthService()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        setSupportActionBar(toolbar)

        nav_view.bringToFront()
        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setCheckedItem(R.id.nav_map)
        nav_view.setNavigationItemSelectedListener(this)

        getUserData()
    }

    private fun getUserData() {

        val id = authService.getUid()

        if (id != null) {
            firestoreService.getUser(id, object : Callback<User> {
                override fun onSuccess(result: User?) {
                    val navHeader = nav_view.getHeaderView(0)
                    if (result != null) {
                        navHeader.findViewById<TextView>(R.id.username).text = result.name
                    }
                    if (result != null) {
                        navHeader.findViewById<TextView>(R.id.email).text = result.email
                    }
                }

                override fun onFailure(exception: Exception) {
                    Log.e("error", exception.message.toString())
                }

            })
        }
    }

    override fun onBackPressed() {
        if(drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else if(container.childCount > 0) {
            onSupportNavigateUp()
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.nav_map -> {
                item.isChecked = true
                supportFragmentManager.findFragmentById(R.id.container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
            }

            R.id.nav_reports -> {
                item.isChecked = true
                startActivity(Intent(this, ReportsActivity::class.java))
            }
            R.id.nav_logOut -> {
                item.isChecked = true
                authService.logOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)

        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        nav_view.setCheckedItem(R.id.nav_map)
        supportFragmentManager.findFragmentById(R.id.container)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        return super.onSupportNavigateUp()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(
            Style.TRAFFIC_DAY
        ) { style ->
            etSearchBar.setOnClickListener { autocompletePlace() }
            enableLocationComponent(style);
        }

        mapboxMap.addOnMapLongClickListener { point ->
            openCrimeForm(point.latitude.toString(), point.longitude.toString())
            true
        }

    }


    private fun autocompletePlace() {

        val autocompleteFragment: PlaceAutocompleteFragment = PlaceAutocompleteFragment.newInstance(
            getString(
                R.string.mapbox_access_token
            )
        )

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(carmenFeature: CarmenFeature) {
                Toast.makeText(
                    this@MainActivity,
                    carmenFeature.text(), Toast.LENGTH_LONG
                ).show()
                supportFragmentManager.findFragmentById(R.id.container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                };
            }

            override fun onCancel() {
                supportFragmentManager.findFragmentById(R.id.container)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                };
            }
        })

        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(R.id.container, autocompleteFragment)
        transaction.commit()
    }

    private fun openCrimeForm(latitude: String, longitude: String) {
        val fragment: Fragment = CrimeFormFragment()

        val bundle = Bundle()

        bundle.putDouble("latitude", latitude.toDouble())
        bundle.putDouble("longitude", longitude.toDouble())

        fragment.arguments = bundle

        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
        transaction.commit()
    }

    /**
     * Initialize the Maps SDK's LocationComponent
     */

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(@NonNull loadedMapStyle: Style) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Get an instance of the component
            val locationComponent = mapboxMap.locationComponent

            // Set the LocationComponent activation options
            val locationComponentActivationOptions =
                LocationComponentActivationOptions.builder(this, loadedMapStyle)
                    .useDefaultLocationEngine(false)
                    .build()

            // Activate with the LocationComponentActivationOptions object
            locationComponent.activateLocationComponent(locationComponentActivationOptions)


            locationComponent.isLocationComponentEnabled = true

            // Set the component's camera mode
            locationComponent.cameraMode = CameraMode.TRACKING

            // Set the component's render mode
            locationComponent.renderMode = RenderMode.COMPASS
            initLocationEngine()
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    /**
     * Set up the LocationEngine and the parameters for querying the device's location
     */

    @SuppressLint("MissingPermission")
    private fun initLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(this)
        val request = LocationEngineRequest.Builder(DEFAULT_INTERVAL_IN_MILLISECONDS)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .setMaxWaitTime(DEFAULT_MAX_WAIT_TIME).build()
        locationEngine.requestLocationUpdates(request, callback, mainLooper)
        locationEngine.getLastLocation(callback)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String?>?) {
//        Toast.makeText(
//            this, R.string.user_location_permission_explanation,
//            Toast.LENGTH_LONG
//        ).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            mapboxMap.getStyle { style -> enableLocationComponent(style) }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        nav_view.setCheckedItem(R.id.nav_map)
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Prevent leaks
        locationEngine.removeLocationUpdates(callback)
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }


    /*--------------------------------------Internal class------------------------------------------*/

    private class LocationChangeListeningActivityLocationCallback(activity: MainActivity?) :
        LocationEngineCallback<LocationEngineResult> {
        private val activityWeakReference: WeakReference<MainActivity> = WeakReference(activity)

        /**
         * The LocationEngineCallback interface's method which fires when the device's location has changed.
         *
         * @param result the LocationEngineResult object which has the last known location within it.
         */
        override fun onSuccess(result: LocationEngineResult) {
            val activity: MainActivity? = activityWeakReference.get()
            if (activity != null) {
                val location = result.lastLocation ?: return

        // Create a Toast which displays the new location's coordinates
//                Toast.makeText(
//                    activity, String.format(
//                        activity.getString(R.string.new_location), result.lastLocation!!
//                            .latitude.toString(), result.lastLocation!!.longitude.toString()
//                    ),
//                    Toast.LENGTH_SHORT
//                ).show()

        // Pass the new location to the Maps SDK's LocationComponent
                activity.mapboxMap.locationComponent.forceLocationUpdate(location)
            }
        }

        /**
         * The LocationEngineCallback interface's method which fires when the device's location can't be captured
         *
         * @param exception the exception message
         */
        override fun onFailure(exception: Exception) {
            val activity: MainActivity? = activityWeakReference.get()
            if (activity != null) {
                Toast.makeText(
                    activity, exception.localizedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

}