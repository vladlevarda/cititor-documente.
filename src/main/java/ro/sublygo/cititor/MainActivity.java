package ro.sublygo.cititor;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> chooserCallback;
    private String fileName = "";
    private String fileData = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (chooserCallback != null) {
                    chooserCallback.onReceiveValue(null);
                }
                chooserCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                });
                try {
                    startActivityForResult(i, 1);
                } catch (Exception e) {
                    chooserCallback = null;
                    return false;
                }
                return true;
            }
        });

        readIncoming(getIntent());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && chooserCallback != null) {
            chooserCallback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            chooserCallback = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void readIncoming(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri uri = intent.getData();
        if (uri == null) return;
        try {
            String name = queryName(uri);
            if (name == null) name = "document";
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "";
            String low = name.toLowerCase();
            if (!low.endsWith(".pdf") && !low.endsWith(".docx")) {
                if (mime.contains("pdf")) name = name + ".pdf";
                else if (mime.contains("wordprocessingml")) name = name + ".docx";
            }
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            fileName = name;
            fileData = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            fileName = "";
            fileData = "";
        }
    }

    private String queryName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception e) {
            // ignorat
        } finally {
            if (c != null) c.close();
        }
        return uri.getLastPathSegment();
    }

    private class Bridge {
        @JavascriptInterface
        public String getName() {
            return fileName;
        }

        @JavascriptInterface
        public String getData() {
            return fileData;
        }
    }
}
