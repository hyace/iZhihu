package com.gracecode.iZhihu.task;

import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import com.gracecode.iZhihu.db.ThumbnailsDatabase;
import com.gracecode.iZhihu.R;
import com.gracecode.iZhihu.util.Helper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * <p/>
 * User: mingcheng
 * Date: 13-5-9
 */
public class FetchThumbnailTask extends BaseTasks<Void, Integer, Integer> {
    private static final String USER_AGENT_STRING = "Mozilla/5.0 (Linux; U; Android 4.2.1; en-us; device Build/FRG83)" +
            " AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Safari/533.1";

    public static final int DEFAULT_HEIGHT = 0;
    public static final int DEFAULT_WIDTH = 0;
    private static final int DOWNLOAD_NOTIFY_ID = 0;
    private final ThumbnailsDatabase mDatabase;
    private final int TIMEOUT_SECONDS = 5000;
    private final DefaultHttpClient httpClient;
    private final List<String> urls;
    private final Context context;
    private final NotificationManager mNotificationManager;
    private NotificationCompat.Builder mNotificationCompat;


    public FetchThumbnailTask(Context context, ThumbnailsDatabase database, List<String> urls, BaseTasks.Callback callback) {
        super(context, callback);

        this.mDatabase = database;
        this.httpClient = new DefaultHttpClient();
        this.urls = urls;
        this.context = context;

        HttpParams httpParams = httpClient.getParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_SECONDS);
        HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_SECONDS);
        HttpProtocolParams.setUserAgent(httpParams, USER_AGENT_STRING);

        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        int downloaded = 0;
        for (int i = 0, size = urls.size(); i < size; i++) {
            String url = urls.get(i);
            if (!mDatabase.isCached(url)) {
                HttpGet getRequest;
                HttpResponse httpResponse;
                HttpEntity entity;
                try {
                    getRequest = new HttpGet(url);
                    getRequest.setHeader("Accept-Language", "zh-cn");
                    getRequest.setHeader("Accept-Encoding", "gzip, deflate");
                    getRequest.setHeader("Connection", "Keep-Alive");
                    getRequest.setHeader("Referer", url);

                    httpResponse = httpClient.execute(getRequest);
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (statusCode == HttpStatus.SC_OK) {
                        entity = httpResponse.getEntity();
                        File localCacheFile = new File(getLocalPath());

                        if (Helper.putFileContent(localCacheFile, entity.getContent())) {
                            Header contentType = httpResponse.getFirstHeader("Content-Type");
                            boolean isCached = mDatabase.markAsCached(url,
                                    localCacheFile.getAbsolutePath(),
                                    contentType.getValue(),
                                    statusCode
                            );

                            if (isCached) {
                                publishProgress(i);
                                downloaded++;
                            }
                        }

                    } else {
                        mDatabase.markAsCached(url, null, null, statusCode);
                        getRequest.abort();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        }

        return downloaded;
    }

    private String getLocalPath() {
        long timeStamp = System.currentTimeMillis();
        File cacheDir = mDatabase.getLocalCacheDirectory();
        return cacheDir + File.separator + timeStamp;
    }

    @Override
    protected void onPreExecute() {
        String text = context.getString(R.string.downloading_offline_image);
        mNotificationCompat = new NotificationCompat.Builder(context);
        mNotificationCompat.setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setTicker(text)
                .setSmallIcon(R.drawable.ic_downloading);

        if (urls.size() > 0) {
            mNotificationCompat.setProgress(urls.size(), 0, false);
            mNotificationManager.notify(DOWNLOAD_NOTIFY_ID, mNotificationCompat.build());
        }

        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        for (Integer value : values) {
            mNotificationCompat
                    //.setTicker(value + "/" + urls.size())
                    .setProgress(urls.size(), value, false);
            mNotificationManager.notify(DOWNLOAD_NOTIFY_ID, mNotificationCompat.build());
        }

        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Integer result) {
        String text = String.format(context.getString(R.string.downloaded_offline_image_complete), result);
        mNotificationCompat.setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setTicker(text)
                .setSmallIcon(R.drawable.ic_launcher);

        if (result != 0) {
            mNotificationCompat.setProgress(0, 0, false);
            mNotificationManager.notify(DOWNLOAD_NOTIFY_ID, mNotificationCompat.build());
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mNotificationManager.cancel(DOWNLOAD_NOTIFY_ID);
            mDatabase.close();
        }

        super.onPostExecute(result);
    }

    @Override
    protected void onCancelled() {

    }
}
