package com.google.firebase.quickstart.firebasestorage;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener {

    private static final String TAG = "MainActivity";

    private static final int RC_TAKE_PICTURE = 101;

    private static final String KEY_FILE_URI = "key_file_uri";

    private BroadcastReceiver mDownloadReceiver;
    private ProgressDialog mProgressDialog;
    private FirebaseAuth mAuth;
    private Uri mFileUri = null;

    // [START declare_ref]
    private StorageReference mStorageRef;
    // [END declare_ref]

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize Firebase Storage Ref
        // [START get_storage_ref]
        mStorageRef = FirebaseStorage.getInstance()
                .getReference(getString(R.string.google_storage_bucket));
        // [END get_storage_ref]

        // Click listeners
        findViewById(R.id.button_camera).setOnClickListener(this);
        findViewById(R.id.button_sign_in).setOnClickListener(this);
        findViewById(R.id.button_download).setOnClickListener(this);

        // Restore instance state
        if (savedInstanceState != null) {
            mFileUri = savedInstanceState.getParcelable(KEY_FILE_URI);
        }

        // Download receiver
        mDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "downloadReceiver:onReceive:" + intent);
                hideProgressDialog();

                if (MyDownloadService.ACTION_COMPLETED.equals(intent.getAction())) {
                    String path = intent.getStringExtra(MyDownloadService.EXTRA_DOWNLOAD_PATH);
                    int numBytes = intent.getIntExtra(MyDownloadService.EXTRA_BYTES_DOWNLOADED, 0);

                    // Alert success
                    showMessageDialog("Success", String.format(Locale.getDefault(),
                            "%d bytes downloaded from %s", numBytes, path));
                }

                if (MyDownloadService.ACTION_ERROR.equals(intent.getAction())) {
                    String path = intent.getStringExtra(MyDownloadService.EXTRA_DOWNLOAD_PATH);

                    // Alert failure
                    showMessageDialog("Error", String.format(Locale.getDefault(),
                            "Failed to download from %s", path));
                }
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUI(mAuth.getCurrentUser());

        // Register download receiver
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mDownloadReceiver, MyDownloadService.getIntentFilter());
    }

    @Override
    public void onStop() {
        super.onStop();

        // Unregister download receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDownloadReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelable(KEY_FILE_URI, mFileUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" + data);
        if (requestCode == RC_TAKE_PICTURE) {
            if (resultCode == RESULT_OK) {
                if (mFileUri != null) {
                    uploadFromUri(mFileUri);
                } else {
                    Log.w(TAG, "File URI is null");
                }
            } else {
                Toast.makeText(this, "Taking picture failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // [START upload_from_uri]
    private void uploadFromUri(Uri fileUri) {
        Log.d(TAG, "uploadFromUri:" + fileUri.toString());

        // [START get_child_ref]
        // Get a reference to store file at photos/<FILENAME>.jpg
        final StorageReference photoRef = mStorageRef.child("photos")
                .child(fileUri.getLastPathSegment());
        // [END get_child_ref]

        // Upload file to Firebase Storage
        // [START_EXCLUDE]
        showProgressDialog();
        // [END_EXCLUDE]
        photoRef.putFile(fileUri)
                .addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Upload succeeded
                        Log.d(TAG, "uploadFromUri:onSuccess");

                        // Get the public download URL
                        Uri downloadUrl = taskSnapshot.getMetadata().getDownloadUrl();

                        // [START_EXCLUDE]
                        hideProgressDialog();
                        ((TextView) findViewById(R.id.picture_download_uri))
                                .setText(downloadUrl.toString());
                        findViewById(R.id.button_download).setVisibility(View.VISIBLE);
                        // [END_EXCLUDE]
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        // Upload failed
                        Log.w(TAG, "uploadFromUri:onFailure", throwable);

                        // [START_EXCLUDE]
                        hideProgressDialog();
                        Toast.makeText(MainActivity.this, "Error: upload failed",
                                Toast.LENGTH_SHORT).show();
                        findViewById(R.id.button_download).setVisibility(View.GONE);
                        // [END_EXCLUDE]
                    }
                });
    }
    // [END upload_from_uri]

    private void launchCamera() {
        // Create intent
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Choose file storage location
        File file = new File(getExternalCacheDir(), UUID.randomUUID().toString() + ".jpg");
        mFileUri = Uri.fromFile(file);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);

        // Launch intent
        startActivityForResult(takePictureIntent, RC_TAKE_PICTURE);
    }


    private void signInAnonymously() {
        // Sign in anonymously. Authentication is required to read or write from Firebase Storage.
        showProgressDialog();
        mAuth.signInAnonymously()
                .addOnSuccessListener(this, new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        Log.d(TAG, "signInAnonymously:SUCCESS");
                        hideProgressDialog();
                        updateUI(authResult.getUser());
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        Log.e(TAG, "signInAnonymously:FAILURE", throwable);
                        hideProgressDialog();
                        updateUI(null);
                    }
                });
    }

    private void beginDownload() {
        // Get path
        String path = "photos/" + mFileUri.getLastPathSegment();

        // Kick off download service
        Intent intent = new Intent(this, MyDownloadService.class);
        intent.setAction(MyDownloadService.ACTION_DOWNLOAD);
        intent.putExtra(MyDownloadService.EXTRA_DOWNLOAD_PATH, path);
        startService(intent);

        // Show loading spinner
        showProgressDialog();
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            findViewById(R.id.layout_signin).setVisibility(View.GONE);
            findViewById(R.id.layout_storage).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.layout_signin).setVisibility(View.VISIBLE);
            findViewById(R.id.layout_storage).setVisibility(View.GONE);
        }
    }

    private void showMessageDialog(String title, String message) {
        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .create();
        ad.show();
    }

    private void showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage("Loading...");
            mProgressDialog.setIndeterminate(true);
        }

        mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_camera:
                launchCamera();
                break;
            case R.id.button_sign_in:
                signInAnonymously();
                break;
            case R.id.button_download:
                beginDownload();
                break;
        }
    }
}