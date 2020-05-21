package com.google.ar.sceneform.samples.solarsystem.test;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.samples.solarsystem.DemoUtils;
import com.google.ar.sceneform.samples.solarsystem.R;
import com.google.ar.sceneform.samples.solarsystem.SolarActivity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestActivity extends AppCompatActivity {

    CompletableFuture<ModelRenderable> model1;
    CompletableFuture<ModelRenderable> model2;
    CompletableFuture<ModelRenderable> model3;

    // renderables
    ModelRenderable model1Renderable;
    ModelRenderable model2Renderable;
    ModelRenderable model3Renderable;

    ArSceneView arSceneView;

    private static final int RC_PERMISSIONS = 0x123;
    private boolean cameraPermissionRequested;

    private GestureDetector gestureDetector;
    private Snackbar loadingMessageSnackbar = null;
    private boolean hasPlacedModel = false;
    private boolean loadingDone = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!DemoUtils.checkIsSupportedDeviceOrFinish(this)) {
            // Not a supported device.
            return;
        }
        setContentView(R.layout.activity_test);
        arSceneView = findViewById(R.id.ar_scene_view_test);

        model1 = ModelRenderable.builder().setSource(this, Uri.parse("b1s.sfb")).build();

        model2 = ModelRenderable.builder().setSource(this, Uri.parse("sb1s.sfb")).build();

        model3 = ModelRenderable.builder().setSource(this, Uri.parse("LampPost.sfb")).build();

        setUp();

    }

    private void setUp() {
        CompletableFuture.allOf(model1, model2, model3)
                .handle((data, throwable) -> {
                    if (throwable != null) {
                        DemoUtils.displayError(this, "Unable to load renderable", throwable);
                        return null;
                    }
                    try {
                        model1Renderable = model1.get();
                        model2Renderable = model2.get();
                        model3Renderable = model3.get();

                        // Everything finished loading successfully.
                        loadingDone = true;

                    } catch (InterruptedException | ExecutionException ex) {
                        DemoUtils.displayError(this, "Unable to load renderable", ex);
                    }
                    return null;
                });

        // Set up a tap gesture detector.
        gestureDetector =
                new GestureDetector(
                        this,
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                onSingleTap(e);
                                return true;
                            }

                            @Override
                            public boolean onDown(MotionEvent e) {
                                return true;
                            }
                        });

        // Set a touch listener on the Scene to listen for taps.
        arSceneView
                .getScene()
                .setOnTouchListener(
                        (HitTestResult hitTestResult, MotionEvent event) -> {
                            // If the solar system hasn't been placed yet, detect a tap and then check to see if
                            // the tap occurred on an ARCore plane to place the solar system.
                            if (!hasPlacedModel) {
                                return gestureDetector.onTouchEvent(event);
                            }

                            // Otherwise return false so that the touch event can propagate to the scene.
                            return false;
                        });

        arSceneView
                .getScene()
                .addOnUpdateListener(
                        frameTime -> {
                            if (loadingMessageSnackbar == null) {
                                return;
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                return;
                            }

                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                if (plane.getTrackingState() == TrackingState.TRACKING) {
                                    hideLoadingMessage();
                                }
                            }
                        });

        // Lastly request CAMERA permission which is required by ARCore.
        DemoUtils.requestCameraPermission(this, RC_PERMISSIONS);
    }

    private void onSingleTap(MotionEvent tap) {
        if (!loadingDone) {
            // We can't do anything yet.
            return;
        }

        Frame frame = arSceneView.getArFrame();
        if (frame != null) {
            if (!hasPlacedModel && tryPlaceModels(tap, frame)) {
                hasPlacedModel = true;
            }
        }
    }

    private boolean tryPlaceModels(MotionEvent tap, Frame frame) {
        if (tap != null && frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    // Create the Anchor.
                    Anchor anchor = hit.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arSceneView.getScene());
                    Node solarSystem = create3DModelsMesh();
                    anchorNode.addChild(solarSystem);
                    return true;
                }
            }
        }
        return false;
    }

    private Node create3DModelsMesh() {
        Node base = new Node();

        Node model1 = new Node();
        model1.setParent(base);
        model1.setLocalPosition(new Vector3(0.0f, 0f, 0.0f));

        Node model1Visual = new Node();
        model1Visual.setParent(model1);
        model1Visual.setRenderable(model1Renderable);
        // right, height, towards
        model1Visual.setLocalPosition(new Vector3(0f, 0f, -2f));

        Node model2Visual = new Node();
        model2Visual.setParent(model1Visual);
        model2Visual.setRenderable(model2Renderable);
        model2Visual.setLocalPosition(new Vector3(1f, 0f, 0f));
//
        Node model3Visual = new Node();
        model3Visual.setParent(model1Visual);
        model3Visual.setRenderable(model3Renderable);
        model3Visual.setLocalPosition(new Vector3(-1f, 0f, 0f));

        return base;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arSceneView == null) {
            return;
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Config.LightEstimationMode lightEstimationMode =
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR;
                Session session =
                        cameraPermissionRequested
                                ? DemoUtils.createArSessionWithInstallRequest(this, lightEstimationMode)
                                : DemoUtils.createArSessionNoInstallRequest(this, lightEstimationMode);
                if (session == null) {
                    cameraPermissionRequested = DemoUtils.hasCameraPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                DemoUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            DemoUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (arSceneView != null) {
            arSceneView.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (arSceneView != null) {
            arSceneView.destroy();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        TestActivity.this.findViewById(android.R.id.content),
                        R.string.plane_finding,
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }
}
