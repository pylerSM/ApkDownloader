package com.jackpf.apkdownloader.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.util.Base64;

import com.jackpf.apkdownloader.Entity.App;
import com.jackpf.apkdownloader.Exception.AuthenticationException;
import com.jackpf.apkdownloader.Exception.PlayApiException;
import com.jackpf.apkdownloader.Proto.Play;

public class PlayApi
{
    /**
     * Authenticator
     */
    private Authenticator authenticator;
    
    /**
     * Request vars
     */
    private final int       SDK_VERSION            = 8013013;
    private final String    DEVICE_SDK_AND_VERSION = "mako:18";
    private final String    OPERATOR               = "T-Mobile";
    private final String    OPERATOR_NUMERIC       = "31020";
    private final String    LOCALE                 = "en";
    private final String    COUNTRY                = "us";
    
    /**
     * Request url
     */
    private final String    REQUEST_URL            = "https://android.clients.google.com/market/api/ApiRequest";
    /**
     * Request version
     */
    private final int       REQUEST_VERSION        = 2;
    
    /**
     * Protobuf fields
     * TODO: Actually use protobuf
     *  Can't quite get the proto file right though
     */
    private final byte[]
        SEP_1   = new byte[]{16},
        SEP_2   = new byte[]{24},
        SEP_3   = new byte[]{34},
        SEP_4   = new byte[]{42},
        SEP_5   = new byte[]{50},
        SEP_6   = new byte[]{58},
        SEP_7   = new byte[]{66},
        SEP_8   = new byte[]{74},
        SEP_9   = new byte[]{82},
        SEP_10  = new byte[]{90},
        SEP_11  = new byte[]{19, 82},
        SEP_12  = new byte[]{10},
        SEP_13  = new byte[]{20},
        SEP_14  = new byte[]{10},
        SEP_15  = new byte[]{10}
    ;
    
    /**
     * Constructor
     * 
     * @param authenticator
     */
    public PlayApi(Authenticator authenticator)
    {
        this.authenticator = authenticator;
    }
    
    /**
     * Get app by package name
     * 
     * @param packageName
     * @return
     * @throws PlayApiException
     */
    public App getApp(String packageName) throws PlayApiException, AuthenticationException
    {
        Serializer.Bytes protoBuf = buildProtoBuf(packageName).getBytes();
        
        byte[] realBytes = new byte[protoBuf.size()];
        for (int i = 0; i < protoBuf.size(); i++) {
            realBytes[i] = protoBuf.get(i);
        }
        
        try {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(REQUEST_URL);
            
            post.addHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setEntity(
                new StringEntity(String.format(
                    "version=%d&request=%s",
                    REQUEST_VERSION,
                    Base64.encodeToString(realBytes, Base64.DEFAULT)
                ))
            );
            
            HttpResponse response = client.execute(post);
            
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new PlayApiException(String.format("Server responded with status code %d", response.getStatusLine().getStatusCode()));
            }
    
            byte[] bin = EntityUtils.toByteArray(response.getEntity());
            
            ByteArrayInputStream bais   = new ByteArrayInputStream(bin);
            GZIPInputStream gzis        = new GZIPInputStream(bais);
            InputStreamReader reader    = new InputStreamReader(gzis);
            BufferedReader in           = new BufferedReader(reader);
    
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            
            return new App(packageName, extractDownloadPath(sb.toString()), extractMarketDA(sb.toString()));
        } catch (Exception e) {
            throw new PlayApiException(e.getMessage());
        }
    }
    
    /**
     * Manually build a protobuf request
     * 
     * @param packageName
     * @return
     */
    private Serializer buildProtoBuf(String packageName) throws AuthenticationException
    {
        Serializer serializer = new Serializer();
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        
        map.put("authToken", authenticator.getToken());
        map.put("SEP_1", SEP_1);
        map.put("isSecure", true);
        map.put("SEP_2", SEP_2);
        map.put("sdkVersion", SDK_VERSION);
        map.put("SEP_3", SEP_3);
        map.put("deviceId", authenticator.getGsfId());
        map.put("SEP_4", SEP_4);
        map.put("deviceAndSdkVersion", DEVICE_SDK_AND_VERSION);
        map.put("SEP_5", SEP_5);
        map.put("locale", LOCALE);
        map.put("SEP_6", SEP_6);
        map.put("country", COUNTRY);
        map.put("SEP_7", SEP_7);
        map.put("operatorAlpha", OPERATOR);
        map.put("SEP_8", SEP_8);
        map.put("simOperatorAlpha", OPERATOR);
        map.put("SEP_9", SEP_9);
        map.put("operatorNumeric", OPERATOR_NUMERIC);
        map.put("SEP_10", SEP_10);
        map.put("simOperatorNumeric", OPERATOR_NUMERIC);
        map.put("SEP_11", SEP_11);
        map.put("packageNameLength", packageName.length() + 2);
        map.put("SEP_12", SEP_12);
        map.put("packageName", packageName);
        map.put("SEP_13", SEP_13);
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            serializer.serialize(entry.getValue());
        }
        
        Serializer tmpSerializer1 = new Serializer(), tmpSerializer2 = new Serializer();
        tmpSerializer1.serialize(SEP_14);
        tmpSerializer2.serialize(getSimOperatorLength(map));
        tmpSerializer1.getBytes().addAll(tmpSerializer2.getBytes());
        tmpSerializer1.serialize(SEP_15);
        
        serializer.getBytes().addAll(0, tmpSerializer1.getBytes());
        
        return serializer;
    }
    
    /**
     * Manually extract the download path from the protobuf response
     * 
     * @param str
     * @return
     * @throws PlayApiException
     */
    private String extractDownloadPath(String str) throws PlayApiException
    {
        Pattern p = Pattern.compile("(?i)https?://[^:]+");
        Matcher m = p.matcher(str);
        
        if (!m.find()) {
            throw new PlayApiException("App not found");
        }
        
        return m.group(0);
    }
    
    /**
     * Manually extract the market da from the protobuf response
     * 
     * @param str
     * @return
     */
    private String extractMarketDA(String str)
    {
        boolean capture = false;
        StringBuilder sb = new StringBuilder();
        
        for (int i = str.lastIndexOf(0x014); i < str.length(); i++) {
            byte b = (byte) str.charAt(i);
            if (b == 0x014) {
                capture = true;
            } else if (capture && b == 0x0c) {
                break;
            } else if (capture) {
                sb.append(str.charAt(i));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * @param map
     * @return
     */
    private int getSimOperatorLength(Map<String, Object> map)
    {
        Serializer tmpSerializer = new Serializer();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            tmpSerializer.serialize(entry.getValue());
            
            if (entry.getKey().equals("simOperatorNumeric")) {
                return tmpSerializer.getBytes().size() + 1;
            }
        }
        
        throw new RuntimeException("Unable to determine sim operator length");
    }
}
