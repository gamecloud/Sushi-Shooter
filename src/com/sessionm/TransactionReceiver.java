/*
 * session M
 * Copyright 2012
 */
package com.sessionm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

/* TransactionReceiver
 * 
 * * <P>A receiver to intercept, process, and store session M transactions.
 * Includes static methods for querying session M transactionData and generating odid ids (reference http://code.google.com/p/odinmobile/)  
 * 
 * USAGE: 
 * Put the following calls in the onCreate method of the application's MainActivity: 

        Context context = this.getActivityContext();

        TransactionReceiver.start(context);

 */

public class TransactionReceiver extends BroadcastReceiver{

    // The sessionm transaction url
    private static final String TARGET_URL = "https://ads.sessionm.com/transactions.json";


    /*
     * Reports the session M tracking Id if it was not initially sent during the initial intent call
     * @
     * @param  context   The context of the application.
     * @return void
     * 
     */    
    public static void start(Context context){

        Map<String, String> prefInfo  = retrieveTransactionInfo(context);
        
        if(prefInfo.size() == 0 || prefInfo.containsKey("sent") == true)
            return;
        
        String transactionId = prefInfo.get("transactionId");
        
        reportTransactionId(context,transactionId);
    }


    /*
     * Returns the transactionId and the original google analytics referrer string along with any extra parameters or null if the data does not exist
     * 
     * @param  context   The context of the application.
     * @return A hashmap populated with session M transaction info
     * 
     */    
    public static Map<String,String> retrieveTransactionInfo(Context context){

        SharedPreferences remotePrefs = context.getApplicationContext().getSharedPreferences("SMPREFS_FILE", Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE);

        Map<String,?> prefData = remotePrefs.getAll();

        Iterator<?> entryIter = prefData.entrySet().iterator();

        HashMap<String,String> data = new HashMap<String,String>();

        while(entryIter.hasNext() == true){
            @SuppressWarnings("unchecked")
            Entry<String, ?> next = (Entry<String, ?>) entryIter.next();
            data.put(next.getKey(),next.getValue().toString());
        }

        return data;
    }




    /*
     * Returns the ODIN-1 String for the Android device. For devices that have a null
     * or invalid ANDROID_ID (such as the emulator), a null value will be returned.
     * 
     * This code is designed to be built against an Android API level 3 or greater,
     * but supports all Android API levels.
     * 
     * @param  context   the context of the application.
     * 
     * @return           the ODIN-1 string or null if the ANDROID_ID is invalid.
     */
    public static String getOdin(Context context){
        return ODIN.getODIN1(context);
    }


    /*
     * Reports the session M transaction to the server
     * 
     * This code is designed to be built against an Android API level 3 or greater,
     * but supports all Android API levels.
     * 
     * @param  String the session M transaction id
     * 
     * @return  boolean true if the transaction was successfully reported 
     */
    public static boolean reportTransactionId(Context context,String transactionId){

        HttpURLConnection conn = null;
        String agent = "Mozilla/4.0";
        String encodedData = "";
        if(transactionId == null || transactionId.length() == 0){
            // if there is no transaction id , generate an odin and send it up with the current app's package name
            transactionId = getOdin(context);
            encodedData = "id2=" + context.getApplicationContext().getPackageName() + ":" + URLEncoder.encode(transactionId);
        }else{
            encodedData = "id=" + URLEncoder.encode(transactionId);
        }
        String type = "application/x-www-form-urlencoded";
        try {
            URL url = new URL(TARGET_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty( "User-Agent", agent );
            conn.setRequestProperty( "Content-Type", type );
            conn.addRequestProperty( "Content-Length",String.valueOf(encodedData.length()));
            conn.setDoInput(true);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write( encodedData.getBytes());
            int rc = conn.getResponseCode();
            
            if(rc >= 200 && rc < 300){
                
                
                DataInputStream br = new DataInputStream(conn.getInputStream());
                String line = "";
                String content = "";
                while((line = br.readLine()) != null )
                    content += line;

                JSONObject respJson = new JSONObject(content);
                if(respJson.optString("status").equalsIgnoreCase("ok") == true){
                    SharedPreferences remotePrefs = context.getApplicationContext().getSharedPreferences("SMPREFS_FILE", Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE);
                    remotePrefs.edit().putString("sent", "true").commit();
                    return true;
                }
            }
        }
        catch( IOException e ){
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }


    private static HashMap<String,String> parseReferrer(String gaReferrerString){

        String referrerString = "";
        HashMap<String, String> referralParams = new HashMap<String, String> ();
        try {
            referrerString = URLDecoder.decode(gaReferrerString,"x-www-form-urlencoded");
            String[] params = referrerString.split("&"); 

            for (String param : params){
                String[] pair = param.split("="); // $NON-NLS-1$
                referralParams.put(pair[0], pair[1]);
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }catch (ArrayIndexOutOfBoundsException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return referralParams;
    }

    @Override
    public void onReceive(Context context, Intent infoIntent) {

        if(infoIntent == null)
            return;
        Bundle extras = infoIntent.getExtras();
        String action = infoIntent.getAction();

        // if the intent is google analytics we set the referrer param and the transaction id to the analytics referrer as well
        if(action == null || action.equalsIgnoreCase("com.android.vending.INSTALL_REFERRER") == false )
            return;

        String transactionId = "undefined";
        String referrerStr = "";

        //grab the referrer parameter from the analytics tracking url
        if(extras.containsKey("referrer"))
            referrerStr = extras.getString("referrer"); 

        // forward the intent to other analytics receivers specified in the meta-data portion of this receivers xml
        forwardAnalyticsIntent(context,infoIntent); 

        HashMap<String,String> rParams =  parseReferrer(referrerStr);

        // If the analytics source is sessionm and an id is present (in the utm_content param) we process this intent
        if(rParams.containsKey("utm_source") == true && rParams.get("utm_source").equalsIgnoreCase("sessionm") && rParams.containsKey("utm_content") == true) 
            transactionId = rParams.get("utm_content");
        else
            return; 

        // store the transaction data into local preferences to retrieve it later
        storeSesssionMTransaction(context, referrerStr, transactionId); 

        // if the transaction was sent to the server, mark it so we don't re-send it
        reportTransactionId(context,transactionId);


    }


    private void storeSesssionMTransaction(Context context,String referrerString,String transactionId) {
        SharedPreferences remotePrefs = context.getApplicationContext().getSharedPreferences("SMPREFS_FILE", Context.MODE_WORLD_READABLE|Context.MODE_WORLD_WRITEABLE);
        Editor edit = remotePrefs.edit();
        edit.putString("referrer", referrerString);
        edit.putString("transactionId", transactionId);
        edit.commit();
    }

    private void forwardAnalyticsIntent(Context context,Intent analyticsIntent){

        try{

            ComponentName component = new ComponentName(context,this.getClass());

            PackageManager pm = context.getApplicationContext().getPackageManager();

            ActivityInfo receiverInfo = pm.getReceiverInfo(component, PackageManager.GET_META_DATA);

            Bundle metaData = receiverInfo.metaData;

            if(metaData != null){ // iterate through meta data in the receiver's xml and forward the analytics intent

                Set<String> keyset = metaData.keySet();
                Iterator<String> iter = keyset.iterator();

                while(iter.hasNext() == true){

                    String key = iter.next();
                    String alternateReceiver =  metaData.getString(key);
                    ((BroadcastReceiver)Class.forName(alternateReceiver).newInstance()).onReceive(context, analyticsIntent);

                }
            }

        }catch(NameNotFoundException e){
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private static class ODIN {
        private final static String TAG = "ODIN";
        private final static String SHA1_ALGORITHM = "SHA-1";
        private final static String CHAR_SET = "iso-8859-1";


        public static String getODIN1(Context context) {
            String androidId;
            try {
                androidId = Settings.System.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            } catch (Exception e) {

                // In Android API levels 1-2, Settings.Secure wasn't implemented.
                // Fall back to deprecated methods.
                try {
                    androidId = Settings.System.getString(context.getContentResolver(), Settings.System.ANDROID_ID);
                } catch (Exception e1) {
                    Log.i(TAG, "Error generating ODIN-1: ", e1);
                    return null;
                }
            }

            return SHA1(androidId);
        }

        private static String convertToHex(byte[] data) {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < data.length; i++) {
                int halfbyte = (data[i] >>> 4) & 0x0F;
                int two_halfs = 0;
                do {
                    if ((0 <= halfbyte) && (halfbyte <= 9))
                        buf.append((char) ('0' + halfbyte));
                    else
                        buf.append((char) ('a' + (halfbyte - 10)));
                    halfbyte = data[i] & 0x0F;
                } while(two_halfs++ < 1);
            }
            return buf.toString();
        }

        private static String SHA1(String text) {
            try {
                MessageDigest md;
                md = MessageDigest.getInstance(SHA1_ALGORITHM);
                byte[] sha1hash = new byte[40];
                md.update(text.getBytes(CHAR_SET), 0, text.length());
                sha1hash = md.digest();

                return convertToHex(sha1hash);
            } catch (Exception e) {
                Log.i(TAG, "Error generating generating SHA-1: ", e);
                return null;
            }
        }

    }
}
