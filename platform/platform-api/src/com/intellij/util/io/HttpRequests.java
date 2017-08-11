/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import com.intellij.Patches;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import com.intellij.util.net.ssl.CertificateManager;
import com.intellij.util.net.ssl.UntrustedCertificateStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handy class for reading data from HTTP connections with built-in support for HTTP redirects and gzipped content and automatic cleanup.
 * Usage: <pre>{@code
 * int firstByte = HttpRequests.request(url).connect(new HttpRequests.RequestProcessor<Integer>() {
 *   public Integer process(@NotNull Request request) throws IOException {
 *     return request.getInputStream().read();
 *   }
 * });
 * }</pre>
 * @see URLUtil
 */
public final class HttpRequests {
  private static final Logger LOG = Logger.getInstance(HttpRequests.class);

  private static final int BLOCK_SIZE = 16 * 1024;
  private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;]+)");

  private HttpRequests() { }


  public interface Request {
    @NotNull
    String getURL();

    @NotNull
    URLConnection getConnection() throws IOException;

    @NotNull
    InputStream getInputStream() throws IOException;

    @NotNull
    BufferedReader getReader() throws IOException;

    @NotNull
    BufferedReader getReader(@Nullable ProgressIndicator indicator) throws IOException;

    /** @deprecated Called automatically on open connection. Use {@link RequestBuilder#tryConnect()} to get response code */
    boolean isSuccessful() throws IOException;

    @NotNull
    File saveToFile(@NotNull File file, @Nullable ProgressIndicator indicator) throws IOException;

    @NotNull
    byte[] readBytes(@Nullable ProgressIndicator indicator) throws IOException;

    @NotNull
    String readString(@Nullable ProgressIndicator indicator) throws IOException;
  }

  public interface RequestProcessor<T> {
    T process(@NotNull Request request) throws IOException;
  }

  public interface ConnectionTuner {
    void tune(@NotNull URLConnection connection) throws IOException;
  }

  public static class HttpStatusException extends IOException {
    private int myStatusCode;
    private String myUrl;

    public HttpStatusException(@NotNull String message, int statusCode, @NotNull String url) {
      super(message);
      myStatusCode = statusCode;
      myUrl = url;
    }

    public int getStatusCode() {
      return myStatusCode;
    }

    @NotNull
    public String getUrl() {
      return myUrl;
    }

    @Override
    public String getMessage() {
      return "Status: " + myStatusCode;
    }

    @Override
    public String toString() {
      return super.toString() + ". Status=" + myStatusCode + ", Url=" + myUrl;
    }
  }


  @NotNull
  public static RequestBuilder request(@NotNull String url) {
    return new RequestBuilderImpl(url);
  }

  @NotNull
  public static String createErrorMessage(@NotNull IOException e, @NotNull Request request, boolean includeHeaders) {
    StringBuilder builder = new StringBuilder();

    builder.append("Cannot download '").append(request.getURL()).append("': ").append(e.getMessage());

    try {
      URLConnection connection = request.getConnection();
      if (includeHeaders) {
        builder.append("\n, headers: ").append(connection.getHeaderFields());
      }
      if (connection instanceof HttpURLConnection) {
        HttpURLConnection httpConnection = (HttpURLConnection)connection;
        builder.append("\n, response: ").append(httpConnection.getResponseCode()).append(' ').append(httpConnection.getResponseMessage());
      }
    }
    catch (Throwable ignored) { }

    return builder.toString();
  }


  private static class RequestBuilderImpl extends RequestBuilder {
    private final String myUrl;
    private int myConnectTimeout = HttpConfigurable.CONNECTION_TIMEOUT;
    private int myTimeout = HttpConfigurable.READ_TIMEOUT;
    private int myRedirectLimit = HttpConfigurable.REDIRECT_LIMIT;
    private boolean myGzip = true;
    private boolean myForceHttps;
    private boolean myUseProxy = true;
    private HostnameVerifier myHostnameVerifier;
    private String myUserAgent;
    private String myAccept;
    private ConnectionTuner myTuner;
    private UntrustedCertificateStrategy myUntrustedCertificateStrategy = UntrustedCertificateStrategy.ASK_USER;

    private RequestBuilderImpl(@NotNull String url) {
      myUrl = url;
    }

    @Override
    public RequestBuilder connectTimeout(int value) {
      myConnectTimeout = value;
      return this;
    }

    @Override
    public RequestBuilder readTimeout(int value) {
      myTimeout = value;
      return this;
    }

    @Override
    public RequestBuilder redirectLimit(int redirectLimit) {
      myRedirectLimit = redirectLimit;
      return this;
    }

    @Override
    public RequestBuilder gzip(boolean value) {
      myGzip = value;
      return this;
    }

    @Override
    public RequestBuilder forceHttps(boolean forceHttps) {
      myForceHttps = forceHttps;
      return this;
    }

    @Override
    public RequestBuilder useProxy(boolean useProxy) {
      myUseProxy = useProxy;
      return this;
    }

    @Override
    public RequestBuilder hostNameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
      myHostnameVerifier = hostnameVerifier;
      return this;
    }

    @Override
    public RequestBuilder userAgent(@Nullable String userAgent) {
      myUserAgent = userAgent;
      return this;
    }

    @Override
    public RequestBuilder productNameAsUserAgent() {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        String productName = ApplicationNamesInfo.getInstance().getFullProductName();
        String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
        return userAgent(productName + '/' + version);
      }
      else {
        return userAgent("IntelliJ");
      }
    }

    @Override
    public RequestBuilder accept(@Nullable String mimeType) {
      myAccept = mimeType;
      return this;
    }

    @Override
    public RequestBuilder tuner(@Nullable ConnectionTuner tuner) {
      myTuner = tuner;
      return this;
    }

    @Override
    public RequestBuilder untrustedCertificateStrategy(@NotNull UntrustedCertificateStrategy strategy) {
      myUntrustedCertificateStrategy = strategy;
      return this;
    }

    @Override
    public <T> T connect(@NotNull HttpRequests.RequestProcessor<T> processor) throws IOException {
      return process(this, processor);
    }
  }

  private static class RequestImpl implements Request, AutoCloseable {
    private final RequestBuilderImpl myBuilder;
    private String myUrl;
    private URLConnection myConnection;
    private InputStream myInputStream;
    private BufferedReader myReader;

    private RequestImpl(RequestBuilderImpl builder) {
      myBuilder = builder;
      myUrl = myBuilder.myUrl;
    }

    @NotNull
    @Override
    public String getURL() {
      return myUrl;
    }

    @NotNull
    @Override
    public URLConnection getConnection() throws IOException {
      if (myConnection == null) {
        myConnection = openConnection(myBuilder, this);
      }
      return myConnection;
    }

    @NotNull
    @Override
    public InputStream getInputStream() throws IOException {
      if (myInputStream == null) {
        myInputStream = getConnection().getInputStream();
        if (myBuilder.myGzip && "gzip".equalsIgnoreCase(getConnection().getContentEncoding())) {
          myInputStream = CountingGZIPInputStream.create(myInputStream);
        }
      }
      return myInputStream;
    }

    @NotNull
    @Override
    public BufferedReader getReader() throws IOException {
      return getReader(null);
    }

    @NotNull
    @Override
    public BufferedReader getReader(@Nullable ProgressIndicator indicator) throws IOException {
      if (myReader == null) {
        InputStream inputStream = getInputStream();
        if (indicator != null) {
          int contentLength = getConnection().getContentLength();
          if (contentLength > 0) {
            //noinspection IOResourceOpenedButNotSafelyClosed
            inputStream = new ProgressMonitorInputStream(indicator, inputStream, contentLength);
          }
        }
        myReader = new BufferedReader(new InputStreamReader(inputStream, getCharset(this)));
      }
      return myReader;
    }

    @Override
    public boolean isSuccessful() throws IOException {
      URLConnection connection = getConnection();
      return !(connection instanceof HttpURLConnection) || ((HttpURLConnection)connection).getResponseCode() == 200;
    }

    @Override
    @NotNull
    public byte[] readBytes(@Nullable ProgressIndicator indicator) throws IOException {
      int contentLength = getConnection().getContentLength();
      BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(contentLength > 0 ? contentLength : BLOCK_SIZE);
      NetUtils.copyStreamContent(indicator, getInputStream(), out, contentLength);
      return ArrayUtil.realloc(out.getInternalBuffer(), out.size());
    }

    @NotNull
    @Override
    public String readString(@Nullable ProgressIndicator indicator) throws IOException {
      Charset cs = getCharset(this);
      byte[] bytes = readBytes(indicator);
      return new String(bytes, cs);
    }

    @Override
    @NotNull
    public File saveToFile(@NotNull File file, @Nullable ProgressIndicator indicator) throws IOException {
      FileUtilRt.createParentDirs(file);

      boolean deleteFile = true;
      try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
        NetUtils.copyStreamContent(indicator, getInputStream(), out, getConnection().getContentLength());
        deleteFile = false;
      }
      catch (IOException e) {
        throw new IOException(createErrorMessage(e, this, false), e);
      }
      finally {
        if (deleteFile) {
          FileUtilRt.delete(file);
        }
      }

      return file;
    }

    @Override
    public void close() {
      StreamUtil.closeStream(myInputStream);
      StreamUtil.closeStream(myReader);
      if (myConnection instanceof HttpURLConnection) {
        ((HttpURLConnection)myConnection).disconnect();
      }
    }
  }

  private static <T> T process(RequestBuilderImpl builder, RequestProcessor<T> processor) throws IOException {
    LOG.assertTrue(ApplicationManager.getApplication() == null ||
                   ApplicationManager.getApplication().isUnitTestMode() ||
                   !ApplicationManager.getApplication().isReadAccessAllowed(),
                   "Network shouldn't be accessed in EDT or inside read action");

    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    if (contextLoader != null && shouldOverrideContextClassLoader()) {
      // hack-around for class loader lock in sun.net.www.protocol.http.NegotiateAuthentication (IDEA-131621)
      try (URLClassLoader cl = new URLClassLoader(new URL[0], contextLoader)) {
        Thread.currentThread().setContextClassLoader(cl);
        return doProcess(builder, processor);
      }
      finally {
        Thread.currentThread().setContextClassLoader(contextLoader);
      }
    }
    else {
      return doProcess(builder, processor);
    }
  }

  private static boolean shouldOverrideContextClassLoader() {
    return Patches.JDK_BUG_ID_8032832 &&
           SystemProperties.getBooleanProperty("http.requests.override.context.classloader", true);
  }

  private static <T> T doProcess(RequestBuilderImpl builder, RequestProcessor<T> processor) throws IOException {
    CertificateManager manager = ApplicationManager.getApplication() != null ? CertificateManager.getInstance() : null;
    try (RequestImpl request = new RequestImpl(builder)) {
      if (manager != null) {
        return manager.runWithUntrustedCertificateStrategy(() -> processor.process(request), builder.myUntrustedCertificateStrategy);
      }
      else {
        return processor.process(request);
      }
    }
  }

  private static Charset getCharset(Request request) throws IOException {
    String contentType = request.getConnection().getContentType();
    if (!StringUtil.isEmptyOrSpaces(contentType)) {
      Matcher m = CHARSET_PATTERN.matcher(contentType);
      if (m.find()) {
        try {
          return Charset.forName(StringUtil.unquoteString(m.group(1)));
        }
        catch (IllegalArgumentException e) {
          throw new IOException("unknown charset (" + contentType + ")", e);
        }
      }
    }

    return CharsetToolkit.UTF8_CHARSET;
  }

  private static URLConnection openConnection(RequestBuilderImpl builder, RequestImpl request) throws IOException {
    String url = request.myUrl;

    for (int i = 0; i < builder.myRedirectLimit; i++) {
      if (builder.myForceHttps && StringUtil.startsWith(url, "http:")) {
        request.myUrl = url = "https:" + url.substring(5);
      }

      final URLConnection connection;
      if (!builder.myUseProxy) {
        connection = new URL(url).openConnection(Proxy.NO_PROXY);
      }
      else if (ApplicationManager.getApplication() == null) {
        connection = new URL(url).openConnection();
      }
      else {
        connection = HttpConfigurable.getInstance().openConnection(url);
      }

      if (connection instanceof HttpsURLConnection) {
        if (ApplicationManager.getApplication() != null) {
          try {
            final SSLContext context = CertificateManager.getInstance().getSslContext();
            final SSLSocketFactory factory = context.getSocketFactory();
            if (factory != null) {
              ((HttpsURLConnection)connection).setSSLSocketFactory(factory);
            }
            else {
              LOG.info("SSLSocketFactory is not defined by IDE CertificateManager; Using default SSL configuration to connect to " + url);
            }
          }
          catch (Throwable e) {
            LOG.info("Problems configuring SSL connection to " + url , e);
          }
        }
        else {
          LOG.info("Application is not initialized yet; Using default SSL configuration to connect to " + url);
        }
      }
      connection.setConnectTimeout(builder.myConnectTimeout);
      connection.setReadTimeout(builder.myTimeout);

      if (builder.myUserAgent != null) {
        connection.setRequestProperty("User-Agent", builder.myUserAgent);
      }

      if (builder.myHostnameVerifier != null && connection instanceof HttpsURLConnection) {
        ((HttpsURLConnection)connection).setHostnameVerifier(builder.myHostnameVerifier);
      }

      if (builder.myGzip) {
        connection.setRequestProperty("Accept-Encoding", "gzip");
      }

      if (builder.myAccept != null) {
        connection.setRequestProperty("Accept", builder.myAccept);
      }

      connection.setUseCaches(false);

      if (builder.myTuner != null) {
        builder.myTuner.tune(connection);
      }

      if (connection instanceof HttpURLConnection) {
        HttpURLConnection httpURLConnection = (HttpURLConnection)connection;

        if (LOG.isDebugEnabled()) LOG.debug("connecting to " + url);
        int responseCode = httpURLConnection.getResponseCode();
        if (LOG.isDebugEnabled()) LOG.debug("response: " + responseCode);

        if (responseCode < 200 || responseCode >= 300 && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
          httpURLConnection.disconnect();

          if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            request.myUrl = url = connection.getHeaderField("Location");
            if (url != null) {
              continue;
            }
          }

          String message = IdeBundle.message("error.connection.failed.with.http.code.N", responseCode);
          throw new HttpStatusException(message, responseCode, StringUtil.notNullize(url, "Empty URL"));
        }
      }

      return connection;
    }

    throw new IOException(IdeBundle.message("error.connection.failed.redirects"));
  }
}