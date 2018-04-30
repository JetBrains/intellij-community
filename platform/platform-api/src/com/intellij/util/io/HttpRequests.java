// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.Url;
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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Handy class for reading data from HTTP connections with built-in support for HTTP redirects and gzipped content and automatic cleanup.
 *
 * Usage: <pre>{@code
 * int firstByte = HttpRequests.request(url).connect(request -> request.getInputStream().read());
 * }</pre>
 * @see URLUtil
 * @see <a href="https://github.com/JetBrains/intellij-community/tree/master/platform/platform-api/src/com/intellij/util/io#httprequests">docs</a>
 */
public final class HttpRequests {
  private static final Logger LOG = Logger.getInstance(HttpRequests.class);

  public static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

  private static final int[] REDIRECTS = {
    // temporary redirects
    HttpURLConnection.HTTP_MOVED_TEMP, 307 /* temporary redirect */,
    // permanent redirects
    HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_SEE_OTHER, 308 /* permanent redirect */
  };

  private static final int PERMANENT_IDX = ArrayUtil.indexOf(REDIRECTS, HttpURLConnection.HTTP_MOVED_PERM);

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

    @NotNull
    default String readString() throws IOException {
      return readString(null);
    }

    @NotNull
    CharSequence readChars(@Nullable ProgressIndicator indicator) throws IOException;

    default void write(@NotNull String data) throws IOException {
      write(data.getBytes(StandardCharsets.UTF_8));
    }

    default void write(@NotNull byte[] data) throws IOException {
      HttpURLConnection connection = (HttpURLConnection)getConnection();
      connection.setFixedLengthStreamingMode(data.length);
      try (OutputStream stream = connection.getOutputStream()) {
        stream.write(data);
      }
    }
  }

  public interface RequestProcessor<T> {
    T process(@NotNull Request request) throws IOException;
  }

  public interface ConnectionTuner {
    void tune(@NotNull URLConnection connection) throws IOException;
  }

  public static class HttpStatusException extends IOException {
    private final int myStatusCode;
    private final String myUrl;

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
    public String toString() {
      return super.toString() + ". Status=" + myStatusCode + ", Url=" + myUrl;
    }
  }

  @NotNull
  public static RequestBuilder request(@NotNull Url url) {
    return request(url.toExternalForm());
  }

  @NotNull
  public static RequestBuilder request(@NotNull String url) {
    return new RequestBuilderImpl(url, null);
  }

  @NotNull
  public static RequestBuilder head(@NotNull String url) {
    return new RequestBuilderImpl(url, connection -> ((HttpURLConnection)connection).setRequestMethod("HEAD"));
  }

  @NotNull
  public static RequestBuilder post(@NotNull String url, @Nullable String contentType) {
    return new RequestBuilderImpl(url, rawConnection -> {
      HttpURLConnection connection = (HttpURLConnection)rawConnection;
      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      if (contentType != null) {
        connection.setRequestProperty("Content-Type", contentType);
      }
    });
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
    private boolean myIsReadResponseOnError;
    private HostnameVerifier myHostnameVerifier;
    private String myUserAgent;
    private String myAccept;
    private ConnectionTuner myTuner;
    private final ConnectionTuner myInternalTuner;
    private UntrustedCertificateStrategy myUntrustedCertificateStrategy = null;

    private RequestBuilderImpl(@NotNull String url, @Nullable ConnectionTuner internalTuner) {
      myUrl = url;
      myInternalTuner = internalTuner;
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
    public RequestBuilder isReadResponseOnError(boolean isReadResponseOnError) {
      myIsReadResponseOnError = isReadResponseOnError;
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

    @NotNull
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
        myReader = new BufferedReader(new InputStreamReader(inputStream, getCharset()));
      }
      return myReader;
    }

    @NotNull
    private Charset getCharset() throws IOException {
      return HttpUrlConnectionUtil.getCharset(getConnection());
    }

    @Override
    public boolean isSuccessful() throws IOException {
      URLConnection connection = getConnection();
      return !(connection instanceof HttpURLConnection) || ((HttpURLConnection)connection).getResponseCode() == 200;
    }

    @Override
    @NotNull
    public byte[] readBytes(@Nullable ProgressIndicator indicator) throws IOException {
      return doReadBytes(indicator).toByteArray();
    }

    @NotNull
    private BufferExposingByteArrayOutputStream doReadBytes(@Nullable ProgressIndicator indicator) throws IOException {
      return HttpUrlConnectionUtil.readBytes(getInputStream(), getConnection(), indicator);
    }

    @NotNull
    @Override
    public String readString(@Nullable ProgressIndicator indicator) throws IOException {
      return HttpUrlConnectionUtil.readString(getInputStream(), getConnection(), indicator);
    }

    @NotNull
    @Override
    public CharSequence readChars(@Nullable ProgressIndicator indicator) throws IOException {
      BufferExposingByteArrayOutputStream byteStream = doReadBytes(indicator);
      if (byteStream.size() == 0) {
        return ArrayUtil.EMPTY_CHAR_SEQUENCE;
      }
      else {
        return getCharset().decode(ByteBuffer.wrap(byteStream.getInternalBuffer(), 0, byteStream.size()));
      }
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
    Application app = ApplicationManager.getApplication();
    LOG.assertTrue(app == null || app.isUnitTestMode() || app.isHeadlessEnvironment() || !app.isReadAccessAllowed(),
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
    CertificateManager manager = builder.myUntrustedCertificateStrategy == null || ApplicationManager.getApplication() == null ? null : CertificateManager.getInstance();
    try (RequestImpl request = new RequestImpl(builder)) {
      T result;
      if (manager != null) {
        result = manager.runWithUntrustedCertificateStrategy(() -> processor.process(request), builder.myUntrustedCertificateStrategy);
      }
      else {
        result = processor.process(request);
      }

      URLConnection connection = request.myConnection;
      if (connection instanceof HttpURLConnection && ((HttpURLConnection)connection).getRequestMethod().equals("POST")) {
        // getResponseCode is not checked on connect for POST, because write must be performed before read
        HttpURLConnection urlConnection = (HttpURLConnection)connection;
        int responseCode = urlConnection.getResponseCode();
        if (responseCode >= 400) {
          throwHttpStatusError(urlConnection, request, builder, responseCode);
        }
      }
      return result;
    }
  }

  private static URLConnection openConnection(RequestBuilderImpl builder, RequestImpl request) throws IOException {
    if (builder.myForceHttps && StringUtil.startsWith(request.myUrl, "http:")) {
      request.myUrl = "https:" + request.myUrl.substring(5);
    }

    for (int i = 0; i < builder.myRedirectLimit; i++) {
      String url = request.myUrl;

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
        configureSslConnection(url, (HttpsURLConnection)connection);
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

      if (builder.myInternalTuner != null) {
        builder.myInternalTuner.tune(connection);
      }

      if (builder.myTuner != null) {
        builder.myTuner.tune(connection);
      }

      checkRequestHeadersForNulBytes(connection);

      if (!(connection instanceof HttpURLConnection)) {
        return connection;
      }

      HttpURLConnection httpURLConnection = (HttpURLConnection)connection;
      String method = httpURLConnection.getRequestMethod();
      if (method.equals("POST")) {
        return connection;
      }

      LOG.assertTrue(method.equals("GET") || method.equals("HEAD"), "'" + method + "' not supported; please use GET, HEAD or POST");

      if (LOG.isDebugEnabled()) LOG.debug("connecting to " + url);
      int responseCode = httpURLConnection.getResponseCode();
      if (LOG.isDebugEnabled()) LOG.debug("response: " + responseCode);

      if (responseCode < 200 || responseCode >= 300 && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
        int idx = ArrayUtil.indexOf(REDIRECTS, responseCode);
        if (idx >= 0) {
          httpURLConnection.disconnect();
          url = connection.getHeaderField("Location");
          if (url != null) {
            if (idx >= PERMANENT_IDX) {
              LOG.warn("HTTP response " + responseCode + " for '" + request.myUrl + "'; should be updated to '" + url + "'");
            }
            request.myUrl = url;
            continue;
          }
        }

        return throwHttpStatusError(httpURLConnection, request, builder, responseCode);
      }

      return connection;
    }

    throw new IOException(IdeBundle.message("error.connection.failed.redirects"));
  }

  private static URLConnection throwHttpStatusError(@NotNull HttpURLConnection connection, @NotNull RequestImpl request, @NotNull RequestBuilderImpl builder, int responseCode) throws IOException {
    String message = null;
    if (builder.myIsReadResponseOnError) {
      message = HttpUrlConnectionUtil.readString(connection.getErrorStream(), connection);
    }

    if (StringUtil.isEmpty(message)) {
      message = "Request failed with status code " + responseCode;
    }
    connection.disconnect();
    throw new HttpStatusException(message, responseCode, StringUtil.notNullize(request.myUrl, "Empty URL"));
  }

  private static void configureSslConnection(@NotNull String url, @NotNull HttpsURLConnection connection) {
    if (ApplicationManager.getApplication() == null) {
      LOG.info("Application is not initialized yet; Using default SSL configuration to connect to " + url);
      return;
    }

    try {
      final SSLContext context = CertificateManager.getInstance().getSslContext();
      final SSLSocketFactory factory = context.getSocketFactory();
      if (factory == null) {
        LOG.info("SSLSocketFactory is not defined by IDE CertificateManager; Using default SSL configuration to connect to " + url);
      }
      else {
        connection.setSSLSocketFactory(factory);
      }
    }
    catch (Throwable e) {
      LOG.info("Problems configuring SSL connection to " + url, e);
    }
  }

  /**
   * A lot of web servers would not process request and just return 400 (Bad Request) response if any of request headers contains NUL byte.
   * This method checks if any headers contain NUL byte in value and removes those headers from request.
   * @param httpURLConnection connection to check
   */
  private static void checkRequestHeadersForNulBytes(@NotNull URLConnection httpURLConnection) {
    for (Map.Entry<String, List<String>> header : httpURLConnection.getRequestProperties().entrySet()) {
      boolean shouldBeIgnored = false;
      for (String headerValue : header.getValue()) {
        if (headerValue.contains("\0")) {
          LOG.error(String.format("Problem during request to '%s'. Header's '%s' value contains NUL bytes: '%s'. Omitting this header.",
                                  httpURLConnection.getURL().toString(), header.getKey(), headerValue));
          shouldBeIgnored = true;
          break;
        }
      }
      if (shouldBeIgnored) {
        httpURLConnection.setRequestProperty(header.getKey(), null);
      }
    }
  }
}