/**
 *  Osmo on Android
 *  Aug/2010
 *  NGO Van Luyen
 * $Id$
 *
 */
package com.gpac.Osmo4;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.content.res.AssetManager;

import com.gpac.Osmo4.Osmo4GLSurfaceView;
import com.gpac.Osmo4.Preview;
import com.gpac.Osmo4.R;
import com.gpac.Osmo4.extra.FileChooserActivity;
import com.gpac.Osmo4.logs.GpacLogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import com.lge.real3d.Real3D;
import com.lge.real3d.Real3DInfo;
/**
 * The main Osmo4 activity, used to launch everything
 * 
 * @version $Revision$
 * 
 */
public class Osmo4 extends Activity implements GpacCallback {

    // private String[] m_modules_list;
	
	public boolean m3DLibraryLoaded = false;
	private Real3D mReal3D;	

    private boolean shouldDeleteGpacConfig = false;

    /**
     * @return the shouldDeleteGpacConfig
     */
    public synchronized boolean isShouldDeleteGpacConfig() {
        return shouldDeleteGpacConfig;
    }

    /**
     * @param shouldDeleteGpacConfig the shouldDeleteGpacConfig to set
     */
    public synchronized void setShouldDeleteGpacConfig(boolean shouldDeleteGpacConfig) {
        this.shouldDeleteGpacConfig = shouldDeleteGpacConfig;
    }

    private final static String CFG_STARTUP_CATEGORY = "General"; //$NON-NLS-1$

    private final static String CFG_STARTUP_NAME = "StartupFile"; //$NON-NLS-1$

    private boolean keyboardIsVisible = false;

    private final static int DEFAULT_BUFFER_SIZE = 8192;

    private GpacConfig gpacConfig;

    /**
     * Activity request ID for picking a file from local file system
     */
    public final static int PICK_FILE_REQUEST = 1;

    private final static String LOG_OSMO_TAG = "Osmo4"; //$NON-NLS-1$

    /**
     * List of all extensions recognized by Osmo
     */
    public final static String OSMO_REGISTERED_FILE_EXTENSIONS = "*.mp4,*.bt,*.xmt,*.xml,*.ts,*.svg,*.mp3,*.m3u8,*.mpg,*.aac,*.m4a,*.jpg,*.png,*.wrl,*.mpd"; //$NON-NLS-1$

    private PowerManager.WakeLock wl = null;

    private Osmo4GLSurfaceView mGLView;

    private GpacLogger logger;

    private ProgressDialog startupProgress;
    
    private LinearLayout gl_view;

    // ---------------------------------------
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        		WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        try {
			Class c = Class.forName("com.lge.real3d.Real3D");
			final String LGE_3D_DISPLAY = "lge.hardware.real3d.barrier.landscape";
			if(this.getPackageManager().hasSystemFeature(LGE_3D_DISPLAY))
				m3DLibraryLoaded = true;
        } 
        catch (ClassNotFoundException e) {
        	m3DLibraryLoaded = false;
        }

		MPEGVSensor.myAct = this;
        MPEGVSensor.initSensorManager((SensorManager) getSystemService(Context.SENSOR_SERVICE));
		MPEGVSensor.initLocationManager((LocationManager)getSystemService(Context.LOCATION_SERVICE));

        final String toOpen;
        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            Uri uri = getIntent().getData();
            if (uri != null) {
                synchronized (this) {
                    toOpen = uri.toString();
                }
            } else
                toOpen = null;
        } else
            toOpen = null;
        if (gpacConfig == null) {
            gpacConfig = new GpacConfig(this);
            if (gpacConfig == null) {
                Log.e(LOG_OSMO_TAG, "Failed to load GPAC config"); //$NON-NLS-1$
                displayPopup(getResources().getString(R.string.failedToLoadGPACConfig),
                             getResources().getString(R.string.failedToLoadGPACConfig));
            } else
                gpacConfig.ensureAllDirectoriesExist();
        }
        if (logger == null)
            logger = new GpacLogger(gpacConfig);
        logger.onCreate();
        if (startupProgress == null) {
            startupProgress = new ProgressDialog(this);
            startupProgress.setCancelable(false);
        }
        startupProgress.setMessage(getResources().getText(R.string.osmoLoading));
        startupProgress.setTitle(R.string.osmoLoading);
        startupProgress.show();
        
        if (mGLView != null) {
            setContentView(R.layout.main);
            if (toOpen != null)
                openURLasync(toOpen);
            // OK, it means activity has already been started
            return;
        }
		
		Preview.context = this;
       
        setContentView(R.layout.main);
		
		mGLView = new Osmo4GLSurfaceView(this);
		
		gl_view = (LinearLayout)findViewById(R.id.surface_gl);
        
        if( m3DLibraryLoaded ) //should be checking wether the terminal is a LG one
        {
        	//TryLoad3DClass(true);
        	mReal3D = new Real3D(mGLView.getHolder());
        	mReal3D.setReal3DInfo(new Real3DInfo(true, Real3D.REAL3D_TYPE_SS, Real3D.REAL3D_ORDER_LR));
        }
        service.submit(new Runnable() {

            @Override
            public void run() {
                // loadAllModules();
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        startupProgress.setIndeterminate(true);
                        startupProgress.setMessage(getResources().getText(R.string.gpacLoading));
                        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                        WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, LOG_OSMO_TAG);
                        if (wl != null)
                            wl.acquire();
                        synchronized (Osmo4.this) {
                            Osmo4.this.wl = wl;
                        }

                        Osmo4Renderer renderer = new Osmo4Renderer(Osmo4.this, gpacConfig, toOpen);
                        mGLView.setRenderer(renderer);
                       
                        gl_view.addView(mGLView);
                    }
                });

            }
        });
        // creates no media files if it does not exist
        service.submit(new Runnable() {

            @Override
            public void run() {
                for (String s : new String[] { gpacConfig.getGpacConfigDirectory(), gpacConfig.getGpacCacheDirectory() }) {
                    File noMedia = new File(s, ".nomedia"); //$NON-NLS-1$
                    if (!noMedia.exists()) {
                        try {
                            noMedia.createNewFile();
                        } catch (IOException e) {
                            Log.w(LOG_OSMO_TAG, "Failed to create " + noMedia.getAbsolutePath(), e); //$NON-NLS-1$
                        }
                    }
                }
            }
        });
        
        //copy GUI elements
        copyGui(gpacConfig);

    }
    
    /*
     * Copy GUI elements
     *
     * @param config
     *
     * Reference: http://stackoverflow.com/questions/4447477/android-how-to-copy-files-from-assets-folder-to-sdcard
    */
    private final static String GUI_ROOT_ASSET_DIR = "gui";
    private void copyGui(GpacConfig config) {
     		StringBuilder sb = new StringBuilder();
     		HashMap<String, Throwable> exceptions = new HashMap<String, Throwable>();
				AssetManager assetManager = getAssets();
				String[] list = null;
				try {
				    list = assetManager.list(GUI_ROOT_ASSET_DIR);
				} catch (IOException e) {
				    Log.e(LOG_OSMO_TAG, "Failed to get asset file list.", e);
				    exceptions.put("Failed to get asset file list", e);
				}
				for(String path : list) {
				    try {
				      copyFileOrDir(config, path);
				    } catch(IOException e) {
				        Log.e(LOG_OSMO_TAG, "Failed to copy: " + path, e);
				        exceptions.put("Failed to copy " + path, e);
				    }       
				}
				
				if (!exceptions.isEmpty()) {
					try {
              PrintStream out = new PrintStream(config.getGpacConfigDirectory() + "debug_gui.txt", "UTF-8"); //$NON-NLS-1$//$NON-NLS-2$
	            sb.append("*** Exceptions:\n"); //$NON-NLS-1$
	            for (Map.Entry<String, Throwable> ex : exceptions.entrySet()) {
	                sb.append(ex.getKey()).append(": ") //$NON-NLS-1$
	                  .append(ex.getValue().getLocalizedMessage())
	                  .append('(')
	                  .append(ex.getValue().getClass())
	                  .append(")\n"); //$NON-NLS-1$
	            }
              out.println(sb.toString());
              out.flush();
              out.close();
          } catch (Exception e) {
              Log.e(LOG_OSMO_TAG, "Failed to output debug info to debug file", e); //$NON-NLS-1$
          }
				}
		}
		private void copyFileOrDir(GpacConfig config, String path) throws IOException {
				AssetManager assetManager = getAssets();
				String assets[] = null;
				assets = assetManager.list(GUI_ROOT_ASSET_DIR + "/" + path);
				if (assets.length == 0) {
				   copyFile(config, path);
		    } else {
		        String fullPath = config.getGpacGuiDirectory() + path;
		        File dir = new File(fullPath);
		        if (!dir.exists())
		            dir.mkdir();
		        for (int i = 0; i < assets.length; ++i) {
		            copyFileOrDir(config, path + "/" + assets[i]);
		        }
		    }
		}

		private void copyFile(GpacConfig config, String filename) throws IOException {
		// if this file exists, do nothing
				if ((new File(config.getGpacGuiDirectory() + filename).exists()))
					return;
				AssetManager assetManager = getAssets();
				InputStream in = null;
				OutputStream out = null;
			  in = assetManager.open(GUI_ROOT_ASSET_DIR + "/" + filename);
			  String newFileName = config.getGpacGuiDirectory() + filename;
			  out = new FileOutputStream(newFileName);

			  byte[] buffer = new byte[1024];
			  int read;
			  while ((read = in.read(buffer)) != -1) {
			      out.write(buffer, 0, read);
			  }
			  in.close();
			  in = null;
			  out.flush();
			  out.close();
			  out = null;
		}

    // ---------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    private String getRecentURLsFile() {
        return gpacConfig.getGpacConfigDirectory() + "recentURLs.txt"; //$NON-NLS-1$
    }

    private boolean openURL() {
        Future<String[]> res = service.submit(new Callable<String[]>() {

            @Override
            public String[] call() throws Exception {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(getRecentURLsFile()),
                                                                      DEFAULT_ENCODING), DEFAULT_BUFFER_SIZE);

                    String s = null;
                    Set<String> results = new HashSet<String>();
                    while (null != (s = reader.readLine())) {
                        results.add(s);
                    }
                    addAllRecentURLs(results);
                    return results.toArray(new String[0]);
                } finally {
                    if (reader != null)
                        reader.close();
                }
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final AutoCompleteTextView textView = new AutoCompleteTextView(this);
        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        builder.setMessage(R.string.pleaseEnterAnURLtoConnectTo)
               .setCancelable(true)
               .setPositiveButton(R.string.open_url, new DialogInterface.OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                       dialog.cancel();
                       final String newURL = textView.getText().toString();
                       openURLasync(newURL);
                       service.execute(new Runnable() {

                           @Override
                           public void run() {
                               addAllRecentURLs(Collections.singleton(newURL));
                               File tmp = new File(getRecentURLsFile() + ".tmp"); //$NON-NLS-1$
                               BufferedWriter w = null;
                               try {
                                   w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp),
                                                                                 DEFAULT_ENCODING), DEFAULT_BUFFER_SIZE);
                                   Collection<String> toWrite = getAllRecentURLs();
                                   for (String s : toWrite) {
                                       w.write(s);
                                       w.write("\n"); //$NON-NLS-1$
                                   }
                                   w.close();
                                   w = null;
                                   if (tmp.renameTo(new File(getRecentURLsFile())))
                                       Log.e(LOG_OSMO_TAG, "Failed to rename " + tmp + " to " + getRecentURLsFile()); //$NON-NLS-1$//$NON-NLS-2$

                               } catch (IOException e) {
                                   Log.e(LOG_OSMO_TAG, "Failed to write recent URLs to " + tmp, e); //$NON-NLS-1$
                                   try {
                                       if (w != null)
                                           w.close();
                                   } catch (IOException ex) {
                                       Log.e(LOG_OSMO_TAG, "Failed to close stream " + tmp, ex); //$NON-NLS-1$
                                   }
                               }

                           }
                       });
                   }
               })
               .setNegativeButton(R.string.cancel_button, new DialogInterface.OnClickListener() {

                   @Override
                   public void onClick(DialogInterface dialog, int id) {
                       dialog.cancel();
                   }
               });

        textView.setText("http://"); //$NON-NLS-1$
        builder.setView(textView);

        builder.show();
        ArrayAdapter<String> adapter;
        try {
            adapter = new ArrayAdapter<String>(this,
                                               android.R.layout.simple_dropdown_item_1line,
                                               res.get(1, TimeUnit.SECONDS));
            textView.setAdapter(adapter);
        } catch (ExecutionException e) {
            // Ignored
            Log.e(LOG_OSMO_TAG, "Error while parsing recent URLs", e); //$NON-NLS-1$
        } catch (TimeoutException e) {
            Log.e(LOG_OSMO_TAG, "It took too long to parse recent URLs", e); //$NON-NLS-1$
        } catch (InterruptedException e) {
            Log.e(LOG_OSMO_TAG, "Interrupted while parsing recent URLs", e); //$NON-NLS-1$
        }

        return true;
    }

    private final ExecutorService service = Executors.newSingleThreadExecutor();

    private final Set<String> allRecentURLs = new HashSet<String>();

    private synchronized void addAllRecentURLs(Collection<String> urlsToAdd) {
        allRecentURLs.addAll(urlsToAdd);
    }

    private synchronized Collection<String> getAllRecentURLs() {
        return new ArrayList<String>(allRecentURLs);
    }

    private final static Charset DEFAULT_ENCODING = Charset.forName("UTF-8"); //$NON-NLS-1$

    /**
     * Opens a new activity to select a file
     * 
     * @return true if activity has been selected
     */
    private boolean openFileDialog() {
        String title = getResources().getString(R.string.pleaseSelectAFile);
        Uri uriDefaultDir = Uri.fromFile(new File(gpacConfig.getGpacConfigDirectory()));
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PICK);
        // Files and directories
        intent.setDataAndType(uriDefaultDir, "vnd.android.cursor.dir/lysesoft.andexplorer.file"); //$NON-NLS-1$
        // Optional filtering on file extension.
        intent.putExtra("browser_filter_extension_whitelist", OSMO_REGISTERED_FILE_EXTENSIONS); //$NON-NLS-1$
        // Title
        intent.putExtra("explorer_title", title); //$NON-NLS-1$

        try {
            startActivityForResult(intent, PICK_FILE_REQUEST);
            return true;
        } catch (ActivityNotFoundException e) {
            // OK, lets try with another one... (it includes out bundled one)
            intent = new Intent("org.openintents.action.PICK_FILE"); //$NON-NLS-1$
            intent.setData(uriDefaultDir);
            intent.putExtra(FileChooserActivity.TITLE_PARAMETER, title);
            intent.putExtra("browser_filter_extension_whitelist", OSMO_REGISTERED_FILE_EXTENSIONS); //$NON-NLS-1$
            try {
                startActivityForResult(intent, PICK_FILE_REQUEST);
                return true;
            } catch (ActivityNotFoundException ex) {
                // Not found neither... We build our own dialog to display error
                // Note that this should happen only if we did not embed out own intent
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Impossible to find an Intent to choose a file... Cannot open file !") //$NON-NLS-1$
                       .setCancelable(true)
                       .setPositiveButton(R.string.cancel_button, new DialogInterface.OnClickListener() {

                           @Override
                           public void onClick(DialogInterface dialog, int id) {
                               dialog.cancel();
                           }
                       });
                AlertDialog alert = builder.create();
                alert.show();
                return false;
            }
        }
    }

    // ---------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PICK_FILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String url = uri.toString();
                    String file = "file://"; //$NON-NLS-1$
                    if (url.startsWith(file)) {
                        url = uri.getPath();
                    }
                    Log.i(LOG_OSMO_TAG, "Requesting opening local file " + url); //$NON-NLS-1$
                    openURLasync(url);
                }
            }
        }
    }

    private String currentURL;

    private void openURLasync(final String url) {
        service.execute(new Runnable() {

            @Override
            public void run() {
                mGLView.connect(url);
                currentURL = url;
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        setTitle(getResources().getString(R.string.titleWithURL, url));
                    }
                });
            }
        });
    }

    // ---------------------------------------

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    // ---------------------------------------

    private int last_orientation = 0;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.open_url:
                return openURL();
            case R.id.open_file:
                // newGame();
                return openFileDialog();
            case R.id.cleanCache:
                return cleanCache();
            case R.id.showVirtualKeyboard:
                showKeyboard(!keyboardIsVisible);
                return true;
            case R.id.reloadFile:
                openURLasync(currentURL);
                return true;
            case R.id.autoRotate:
                if (item.isChecked()) {
                    item.setChecked(false);
                    last_orientation = this.getRequestedOrientation();
                    int ornt = getResources().getConfiguration().orientation;
                    if (ornt == Configuration.ORIENTATION_PORTRAIT)
                        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    else
                        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    item.setChecked(true);
                    this.setRequestedOrientation(last_orientation);
                }
                return true;
            case R.id.enableDebug:
                if (item.isChecked()) {
                    item.setChecked(false);
                    mGLView.setGpacLogs("all@error"); //$NON-NLS-1$
                } else {
                    item.setChecked(true);
                    mGLView.setGpacLogs("all@debug"); //$NON-NLS-1$
                }
                return true;
            case R.id.setAsStartupFile: {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle(R.string.setAsStartupFileTitle);
                if (currentURL != null) {
                    b.setMessage(getResources().getString(R.string.setAsStartupFileMessage, currentURL));
                    b.setPositiveButton(R.string.setAsStartupFileYes, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mGLView.setGpacPreference(CFG_STARTUP_CATEGORY, CFG_STARTUP_NAME, currentURL);
                        }
                    });
                } else {
                    b.setMessage(getResources().getString(R.string.setAsStartupFileMessageNoURL));
                }
                b.setNegativeButton(R.string.setAsStartupFileNo, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                b.setNeutralButton(R.string.setAsStartupFileNull, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mGLView.setGpacPreference(CFG_STARTUP_CATEGORY, CFG_STARTUP_NAME, null);
                    }
                });
                b.show();
                return true;
            }
            case R.id.resetGpacConfig: {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setCancelable(true);
                b.setTitle(R.string.resetGpacConfig);
                b.setMessage(R.string.resetGpacConfigMessage);
                b.setNegativeButton(R.string.cancel_button, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                b.setPositiveButton(R.string.ok_button, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        setShouldDeleteGpacConfig(true);
                        deleteConfigIfNeeded();
                    }
                });
                b.show();
                return true;
            }
            case R.id.about: {
                Dialog d = new Dialog(this);
                d.setTitle(R.string.aboutTitle);
                d.setCancelable(true);
                d.setContentView(R.layout.about_dialog);
                d.show();
            }
                return true;
            case R.id.quit:
                if ( mGLView != null )
            		mGLView.disconnect();
                this.finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Cleans GPAC cache
     * 
     * @return true if successful
     */
    protected boolean cleanCache() {
        final CharSequence oldTitle = getTitle();
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                startupProgress.setTitle(R.string.cleaningCache);
                startupProgress.setMessage(getResources().getString(R.string.cleaningCache,
                                                                    gpacConfig.getGpacCacheDirectory()));
                startupProgress.setProgress(0);
                startupProgress.setIndeterminate(false);
                startupProgress.show();
            }
        });
        service.submit(new Runnable() {

            @Override
            public void run() {
                File dir = new File(gpacConfig.getGpacCacheDirectory());
                if (!dir.exists() || !dir.canRead() || !dir.canWrite() || !dir.isDirectory()) {
                    return;
                }
                File files[] = dir.listFiles();
                int i = 0;
                for (File f : files) {
                    if (f.isFile())
                        if (!f.delete()) {
                            Log.w(LOG_OSMO_TAG, "Failed to delete file " + f); //$NON-NLS-1$
                        } else {
                            Log.v(LOG_OSMO_TAG, f + " has been deleted"); //$NON-NLS-1$
                        }
                    final int percent = (++i) * 100 / files.length;
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            startupProgress.setProgress(percent);
                        }
                    });
                }
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        startupProgress.setProgress(100);
                        startupProgress.setTitle(oldTitle);
                        startupProgress.dismiss();
                    }
                });
            }
        });
        return true;
    }

    private void deleteConfigIfNeeded() {
        if (isShouldDeleteGpacConfig()) {
            Log.i(LOG_OSMO_TAG, "Deleting GPAC config file..."); //$NON-NLS-1$
            File f = gpacConfig.getGpacConfigFile();
            if (f.exists() && !f.delete()) {
                Log.e(LOG_OSMO_TAG, "Failed to delete " + f.getAbsolutePath()); //$NON-NLS-1$
            }
            f = gpacConfig.getGpacLastRevFile();
            if (f.exists() && !f.delete()) {
                Log.e(LOG_OSMO_TAG, "Failed to delete " + f.getAbsolutePath()); //$NON-NLS-1$
            }
        }
    }

    // ---------------------------------------
    @Override
    protected void onDestroy() {
        deleteConfigIfNeeded();
        service.shutdown();
        logger.onDestroy();
        synchronized (this) {
            if (wl != null)
                wl.release();
        }
        Log.d(LOG_OSMO_TAG, "Disconnecting instance..."); //$NON-NLS-1$
        mGLView.disconnect();
        Log.d(LOG_OSMO_TAG, "Destroying GPAC instance..."); //$NON-NLS-1$
        mGLView.destroy();
        // Deleteing is done two times if GPAC fails to shutdown by crashing...
        deleteConfigIfNeeded();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Handle the back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Ask the user if they want to quit
            new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                                         .setTitle(R.string.quit)
                                         .setMessage(R.string.really_quit)
                                         .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                                             @Override
                                             public void onClick(DialogInterface dialog, int which) {

                                                 // Stop the activity
                                                 if ( mGLView != null )
                                                     mGLView.disconnect();
                                                 Osmo4.this.finish();
                                             }

                                         })
                                         .setNegativeButton(R.string.no, null)
                                         .show();

            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }

    }

    // ---------------------------------------
    // private void loadAllModules() {
    //        Log.i(LOG_OSMO_TAG, "Start loading all modules..."); //$NON-NLS-1$
    // long start = System.currentTimeMillis();
    // byte buffer[] = new byte[1024];
    // int[] ids = getAllRawResources();
    //        String currentRevision = "$Revision$"; //$NON-NLS-1$
    // File revisionFile = gpacConfig.getGpacLastRevFile();
    // boolean fastStartup = false;
    // // We check if we already copied all the modules once without error...
    // if (revisionFile.exists() && revisionFile.canRead()) {
    // BufferedReader r = null;
    // try {
    // r = new BufferedReader(new InputStreamReader(new FileInputStream(revisionFile), DEFAULT_ENCODING),
    // DEFAULT_BUFFER_SIZE);
    // String rev = r.readLine();
    // if (currentRevision.equals(rev)) {
    // fastStartup = true;
    // }
    // } catch (IOException ignored) {
    // } finally {
    // // Exception or not, always close the stream...
    // if (r != null)
    // try {
    // r.close();
    // } catch (IOException ignored) {
    // }
    // }
    // }
    // boolean noErrors = true;
    // final StringBuilder errorsMsg = new StringBuilder();
    // for (int i = 0; i < ids.length; i++) {
    // OutputStream fos = null;
    // InputStream ins = null;
    //            String fn = gpacConfig.getGpacModulesDirectory() + m_modules_list[i] + ".so"; //$NON-NLS-1$
    // File finalFile = new File(fn);
    // // If file has already been copied, not need to do it again
    // if (fastStartup && finalFile.exists() && finalFile.canRead()) {
    //                Log.i(LOG_OSMO_TAG, "Skipping " + finalFile); //$NON-NLS-1$
    // continue;
    // }
    // try {
    // final String msg = getResources().getString(R.string.copying_native_libs,
    // finalFile.getName(),
    // finalFile.getParent());
    // runOnUiThread(new Runnable() {
    //
    // @Override
    // public void run() {
    // startupProgress.setIndeterminate(false);
    // startupProgress.setMessage(msg);
    // }
    // });
    //                Log.i(LOG_OSMO_TAG, "Copying resource " + ids[i] + " to " //$NON-NLS-1$//$NON-NLS-2$
    // + finalFile.getAbsolutePath());
    //                File tmpFile = new File(fn + ".tmp"); //$NON-NLS-1$
    // int read;
    // ins = new BufferedInputStream(getResources().openRawResource(ids[i]), DEFAULT_BUFFER_SIZE);
    // fos = new BufferedOutputStream(new FileOutputStream(tmpFile), DEFAULT_BUFFER_SIZE);
    // while (0 < (read = ins.read(buffer))) {
    // fos.write(buffer, 0, read);
    // }
    // ins.close();
    // ins = null;
    // fos.close();
    // fos = null;
    // if (!tmpFile.renameTo(finalFile)) {
    // if (finalFile.exists() && finalFile.delete() && !tmpFile.renameTo(finalFile))
    //                        Log.e(LOG_OSMO_TAG, "Failed to rename " + tmpFile.getAbsolutePath() + " to " //$NON-NLS-1$//$NON-NLS-2$
    // + finalFile.getAbsolutePath());
    // }
    // final int percent = i * 10000 / ids.length;
    // runOnUiThread(new Runnable() {
    //
    // @Override
    // public void run() {
    // startupProgress.setProgress(percent);
    // }
    // });
    // } catch (IOException e) {
    // noErrors = false;
    //                String msg = "IOException for resource : " + ids[i]; //$NON-NLS-1$
    // errorsMsg.append(msg).append('\n').append(finalFile.getAbsolutePath()).append('\n');
    // errorsMsg.append(e.getLocalizedMessage());
    // Log.e(LOG_OSMO_TAG, msg, e);
    // } finally {
    // if (ins != null) {
    // try {
    // ins.close();
    // } catch (IOException e) {
    //                        Log.e(LOG_OSMO_TAG, "Error while closing read stream", e); //$NON-NLS-1$
    // }
    // }
    // if (fos != null) {
    // try {
    // fos.close();
    // } catch (IOException e) {
    //                        Log.e(LOG_OSMO_TAG, "Error while closing write stream", e); //$NON-NLS-1$
    // }
    // }
    // }
    // }
    // // If no error during copy, fast startup will be enabled for next time
    // if (noErrors && !fastStartup) {
    // BufferedWriter w = null;
    // try {
    // w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(revisionFile), DEFAULT_ENCODING),
    // DEFAULT_BUFFER_SIZE);
    // w.write(currentRevision);
    // w.write('\n');
    // // We add the date as second line to ease debug in case of problem
    // w.write(String.valueOf(new Date()));
    // } catch (IOException ignored) {
    // } finally {
    // if (w != null)
    // try {
    // w.close();
    // } catch (IOException ignored) {
    // }
    // }
    // } else {
    // if (!noErrors) {
    // setShouldDeleteGpacConfig(true);
    //                displayMessage(errorsMsg.toString(), "Errors while copying modules !", GF_Err.GF_IO_ERR.value); //$NON-NLS-1$
    // }
    // }
    //        Log.i(LOG_OSMO_TAG, "Done loading all modules, took " + (System.currentTimeMillis() - start) + "ms."); //$NON-NLS-1$ //$NON-NLS-2$
    // startupProgress.setProgress(100);
    // startupProgress.setIndeterminate(true);
    // runOnUiThread(new Runnable() {
    //
    // @Override
    // public void run() {
    // setTitle(LOG_OSMO_TAG);
    // }
    // });
    // }

    // private int[] getAllRawResources() throws RuntimeException {
    // R.raw r = new R.raw();
    //
    // java.lang.reflect.Field fields[] = R.raw.class.getDeclaredFields();
    // final int ids[] = new int[fields.length];
    // m_modules_list = new String[fields.length];
    //
    // try {
    // for (int i = 0; i < fields.length; i++) {
    // java.lang.reflect.Field f = fields[i];
    // ids[i] = f.getInt(r);
    // m_modules_list[i] = f.getName();
    //                Log.i(LOG_OSMO_TAG, "R.raw." + f.getName() + " = 0x" + Integer.toHexString(ids[i])); //$NON-NLS-1$ //$NON-NLS-2$
    // }
    // } catch (IllegalArgumentException e) {
    // throw new RuntimeException(e);
    // } catch (IllegalAccessException e) {
    // throw new RuntimeException(e);
    // }
    //
    // return ids;
    // }

    // ---------------------------------------

    private String lastDisplayedMessage;

    private void displayPopup(final CharSequence message, final CharSequence title) {
        final String fullMsg = getResources().getString(R.string.displayPopupFormat, title, message);
        synchronized (this) {
			// In case of an error message, always popup it
            if (fullMsg.equals(lastDisplayedMessage) && !title.equals("Error"))
                return;
            lastDisplayedMessage = fullMsg;
        }
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast toast = Toast.makeText(Osmo4.this, fullMsg, Toast.LENGTH_SHORT);
                toast.show();
            }
        });

    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#displayMessage(String, String, int)
     */
    @Override
    public void displayMessage(final String message, final String title, final int status) {
        if (status == GF_Err.GF_OK.value) {
            // displayPopup(message, title);
            Log.d(LOG_OSMO_TAG, message);
        } else {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(GF_Err.getError(status));
                    sb.append(' ');
                    sb.append(title);
                    AlertDialog.Builder builder = new AlertDialog.Builder(Osmo4.this);
                    builder.setTitle(sb.toString());
                    sb.append('\n');
                    sb.append(message);
                    builder.setMessage(sb.toString());
                    builder.setCancelable(true);
                    builder.setPositiveButton(R.string.ok_button, new OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    try {
                        builder.create().show();
                    } catch (WindowManager.BadTokenException e) {
                        // May happen when we close the window and there are still somes messages
                        Log.e(LOG_OSMO_TAG, "Failed to display Message " + sb.toString(), e); //$NON-NLS-1$
                    }
                }
            });
        }
    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#onLog(int, int, String)
     */
    @Override
    public void onLog(int level, int module, String message) {
        logger.onLog(level, module, message);
		if (level == Log.ERROR)
			displayPopup(message, "Error");
    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#onProgress(java.lang.String, int, int)
     */
    @Override
    public void onProgress(final String msg, final int done, final int total) {
        if (Log.isLoggable(LOG_OSMO_TAG, Log.DEBUG))
            Log.d(LOG_OSMO_TAG, "Setting progress to " + done + "/" + total + ", message=" + msg); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                // GPAC sometimes return total = 0
                if (total < 1) {
                    setProgressBarIndeterminate(true);
                } else {
                    int progress = done * 10000 / (total < 1 ? 1 : total);
                    if (progress > 9900)
                        progress = 10000;
                    setProgressBarIndeterminate(false);
                    setProgress(progress);
                }
            }
        });
    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#onGPACReady()
     */
    @Override
    public void onGPACReady() {
        startupProgress.dismiss();
        Log.i(LOG_OSMO_TAG, "GPAC is ready"); //$NON-NLS-1$
    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#onGPACError(java.lang.Throwable)
     */
    @Override
    public void onGPACError(final Throwable e) {
        startupProgress.dismiss();
        Log.e(LOG_OSMO_TAG, "GPAC Error", e); //$NON-NLS-1$
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                // In such case, we force GPAC Configuration file deletion
                setShouldDeleteGpacConfig(true);
                StringBuilder sb = new StringBuilder();
                sb.append("Failed to init GPAC due to "); //$NON-NLS-1$
                sb.append(e.getClass().getSimpleName());
                AlertDialog.Builder builder = new AlertDialog.Builder(Osmo4.this);
                builder.setTitle(sb.toString());
                sb.append('\n');
                sb.append("Description: "); //$NON-NLS-1$
                sb.append(e.getLocalizedMessage());
                sb.append('\n');
                sb.append("Revision: $Revision$"); //$NON-NLS-1$
                sb.append("\nConfiguration information :\n") //$NON-NLS-1$
                  .append(gpacConfig.getConfigAsText());
                builder.setMessage(sb.toString());
                builder.setCancelable(true);
                builder.setPositiveButton(R.string.ok_button, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.create().show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGLView != null)
            mGLView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGLView != null)
            mGLView.onResume();
    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#setCaption(java.lang.String)
     */
    @Override
    public void setCaption(final String newCaption) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                setTitle(newCaption);
            }
        });
    }

    /**
     * @see android.app.Activity#onStop()
     */
    @Override
    protected void onStop() {
        Log.i(LOG_OSMO_TAG, "onStop called on activity"); //$NON-NLS-1$
        super.onStop();
    }

    /**
     * @see com.gpac.Osmo4.GpacCallback#showKeyboard(boolean)
     */
    @Override
    public void showKeyboard(boolean showKeyboard) {
        this.keyboardIsVisible = showKeyboard;

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                InputMethodManager mgr = ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE));
                if ( Osmo4.this.keyboardIsVisible )
                	mgr.showSoftInput(mGLView, 0);
                else
                	mgr.hideSoftInputFromWindow(mGLView.getWindowToken(), 0);
                //mgr.toggleSoftInputFromWindow(mGLView.getWindowToken(), 0, 0);
                mGLView.requestFocus();
            }
        });
    }
}
