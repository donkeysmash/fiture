package com.waterloo.fiture;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class MainActivity extends AppCompatActivity {
    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mat;
    private  static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL = 0;
    private ImageView mMainImage;
    private Button mBtnDetect;
    private Bitmap mCurrBitmap;

    static {
        System.loadLibrary("opencv_java3");
        if (!OpenCVLoader.initDebug())
            Log.d("ERROR", "Unable to load OpenCV");
        else
            Log.d("SUCCESS", "OpenCV loaded");
    }

//    private HSV

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMainImage = (ImageView) findViewById(R.id.image_view);
        mBtnDetect = (Button)findViewById(R.id.btn_detect);

        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {

            String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
            File file = new File(root, "sang");
            Random generator = new Random();
            int n = 10000;
            n = generator.nextInt(n);
            mCurrBitmap = getBitmapFromAsset(getApplicationContext(), "oval.jpg");

            Glide.with(this).asBitmap().load(bitmapToByte(mCurrBitmap))
                    .into(mMainImage);


            mBtnDetect.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Bitmap bmp = mCurrBitmap;
                    Mat src = new Mat ();

                    Utils.bitmapToMat(bmp, src);

//                    Mat colored = new Mat(tmp.rows(), tmp.cols(), CvType.CV_8U, new Scalar(3));
//                    Imgproc.cvtColor(tmp, colored , Imgproc.COLOR_RGB2HSV, 3);

                    Mat hsvFrame = new Mat(src.rows(), src.cols(), CvType.CV_8U, new Scalar(3));
                    Imgproc.cvtColor(src, hsvFrame, Imgproc.COLOR_RGB2HSV, 3);

                    // Mask the image for skin colors
                    Mat skinMask = new Mat(hsvFrame.rows(), hsvFrame.cols(), CvType.CV_8U, new Scalar(3));
                    Core.inRange(hsvFrame, new Scalar(0, 10, 100), new Scalar(10, 255, 255), skinMask);

//                    Core.inRange(colored, new Scalar(0, 0, 0), new Scalar(10, 255, 255), colored);

                    final Size kernelSize = new Size(11, 11);
                    final Point anchor = new Point(-1, -1);
                    final int iterations = 2;

                    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, kernelSize);
                    Imgproc.erode(skinMask, skinMask, kernel, anchor, iterations);
                    Imgproc.dilate(skinMask, skinMask, kernel, anchor, iterations);

                    // blur the mask to help remove noise, then apply the
                    // mask to the frame
                    final Size ksize = new Size(3, 3);

                    Mat skin = new Mat(skinMask.rows(), skinMask.cols(), CvType.CV_8U, new Scalar(3));
                    Imgproc.GaussianBlur(skinMask, skinMask, ksize, 0);
//                    Core.bitwise_and(src, src, skin, skinMask);

                    Imgproc.threshold(skinMask, skinMask, 13, 255, Imgproc.THRESH_OTSU | Imgproc.THRESH_BINARY);

//                    Imgproc.cvtColor(colored, tmp, Imgproc.COLOR_RGB2RGBA);

//                    Imgproc.cvtColor(tmp, tmp, Imgproc.COLOR_RGB2HSV);
//                    Utils.matToBitmap(colored, bmp);


//                    saveImage(bmp, "filtred");

//                    Imgcodecs.imwrite(filePath, colored);
//                    Imgproc.cvtColor(tmp, colored, Imgproc.COLOR_GRAY2BGRA);

                    List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
                    Mat hierarchy = new Mat();
                    Imgproc.findContours(skinMask, contours, hierarchy, Imgproc.RETR_TREE,Imgproc.CHAIN_APPROX_SIMPLE);
                    double maxVal = 0;
                    int maxValIdx = 0;
                    for (int contourIdx = 0; contourIdx < contours.size(); contourIdx++) {
                        Log.d("tag", contours.get(contourIdx).toString());
                        double contourArea = Imgproc.contourArea(contours.get(contourIdx));
                        if (maxVal < contourArea)
                        {
                            maxVal = contourArea;
                            maxValIdx = contourIdx;
                        }
                        Log.d("are", "c");
                    }
//
//                    // Minimum size allowed for consideration
                    MatOfPoint2f approxCurve = new MatOfPoint2f();
                    MatOfPoint2f contour2f = new MatOfPoint2f( contours.get(maxValIdx).toArray() );
//                    //Processing on mMOP2f1 which is in type MatOfPoint2f
                    double approxDistance = Imgproc.arcLength(contour2f, true)*0.02;
                    Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);
//
//                    //Convert back to MatOfPoint
                    MatOfPoint points = new MatOfPoint( approxCurve.toArray() );
//
//                    // Get bounding rect of contour
                    Rect rect = Imgproc.boundingRect(points);

//                    Core.rectangle(mRgba, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 0, 0, 255), 3);

                    Bitmap newbmp = Bitmap.createBitmap(skinMask.cols(), skinMask.rows(), Bitmap.Config.ARGB_8888);

                    Utils.matToBitmap(skinMask, newbmp);
//                    saveImage(bmp, "filtred");
                    final Bitmap coloredBmp = newbmp;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Glide.with(MainActivity.this).asBitmap().load(bitmapToByte(coloredBmp))
                                    .into(mMainImage);
                        }
                    });


//                    Mat circles = new Mat();
//
//                    MediaScannerConnection.scanFile(this, new String[] { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() }, null, new MediaScannerConnection.OnScanCompletedListener() {
//
//                        public void onScanCompleted(String path, Uri uri)
//                        {
//                            Log.i("ExternalStorage", "Scanned " + path + ":");
//                            Log.i("ExternalStorage", "-> uri=" + uri);
//                        }
//                    });
//
//                    List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
//                    Imgproc.findContours(colored, contours, new Mat(),Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
//
//                    for(int i=0; i< contours.size();i++){
//                        MatOfPoint2f temp = new MatOfPoint2f(contours.get(i).toArray());
//                        RotatedRect elipse1 = Imgproc.fitEllipse(temp);
//                    }
                }
            });
        }
    }





    private void saveImage(Bitmap finalBitmap, String image_name) {
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File myDir = new File(root + "/design");
        myDir.mkdirs();
        Random generator = new Random();
        int n = 10000;
        n = generator.nextInt(n);
        String fname = "test-" + n + ".jpg";
        File file = new File(root, fname);
        if (file.exists()) {
            file.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        MediaScannerConnection.scanFile(MainActivity.this, new String[]{file.toString()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });

//        String root = Environment.getExternalStorageDirectory().toString();
//        File myDir = new File(root);
//        myDir.mkdirs();
//        String fname = "Image-" + image_name+ ".jpg";
//        File file = new File(myDir, fname);
//        if (file.exists()) file.delete();
//        Log.i("LOAD", root + fname);
//        try {
//            FileOutputStream out = new FileOutputStream(file);
//            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
//            out.flush();
//            out.close();
//            MediaScannerConnection.scanFile(this, new String[] { Environment.getExternalStorageDirectory().toString() }, null, new MediaScannerConnection.OnScanCompletedListener() {
//
//                public void onScanCompleted(String path, Uri uri)
//                {
//                    Log.i("ExternalStorage", "Scanned " + path + ":");
//                    Log.i("ExternalStorage", "-> uri=" + uri);
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
    private byte[] bitmapToByte(Bitmap bitmap){
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }


    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
        }

        return bitmap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

}
