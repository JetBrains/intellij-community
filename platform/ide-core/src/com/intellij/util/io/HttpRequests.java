// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.Patches;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.Url;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNullElse;

/**
 * <p>Handy class for reading data from URL connections with built-in support for HTTP redirects and gzipped content and automatic cleanup.</p>
 *
 * <h3>Examples</h3>
 *
 * <p>Reading the whole response into a string:<br>
 * {@code HttpRequests.request("https://example.com").readString(progressIndicator)}</p>
 *
 * <p>Downloading a file:<br>
 * {@code HttpRequests.request("https://example.com/file.zip").saveToFile(new File(downloadDir, "temp.zip"), progressIndicator)}</p>
 *
 * <p>Tuning a connection:<br>
 * {@code HttpRequests.request(url).userAgent("IntelliJ").readString()}<br>
 * {@code HttpRequests.request(url).tuner(connection -> connection.setRequestProperty("X-Custom", value)).readString()}</p>
 *
 * <p>Using the input stream to implement custom reading logic:<br>
 * {@code int firstByte = HttpRequests.request("file:///dev/random").connect(request -> request.getInputStream().read())}<br>
 * {@code String firstLine = HttpRequests.request("https://example.com").connect(request -> new BufferedReader(request.getReader()).readLine())}</p>
 *
 * @see HttpStatusException a subclass of IOException, which includes an actual URL and HTTP response code
 * @see URLUtil
 */
public final class HttpRequests {
  private static final Logger LOG = Logger.getInstance(HttpRequests.class);

  public static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

  public static final int CONNECTION_TIMEOUT = SystemProperties.getIntProperty("idea.connection.timeout", 10000);
  public static final int READ_TIMEOUT = SystemProperties.getIntProperty("idea.read.timeout", 60000);
  public static final int REDIRECT_LIMIT = SystemProperties.getIntProperty("idea.redirect.limit", 10);

  public static final int CUSTOM_ERROR_CODE = 451;

  private static final int[] REDIRECTS = {
    // temporary redirects
    HttpURLConnection.HTTP_MOVED_TEMP, 307 /* temporary redirect */,
    // permanent redirects
    HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_SEE_OTHER, 308 /* permanent redirect */
  };

  private HttpRequests() { }

  public interface Request {
    @NotNull String getURL();

    @NotNull URLConnection getConnection() throws IOException;

    @NotNull InputStream getInputStream() throws IOException;

    @NotNull BufferedReader getReader() throws IOException;

    @NotNull BufferedReader getReader(@Nullable ProgressIndicator indicator) throws IOException;

    /** Prefer {@link #saveToFile(Path, ProgressIndicator)}. */
    @ApiStatus.Obsolete
    @SuppressWarnings("IO_FILE_USAGE")
    default @NotNull File saveToFile(@NotNull File file, @Nullable ProgressIndicator indicator) throws IOException {
      return saveToFile(file.toPath(), indicator).toFile();
    }

    @NotNull Path saveToFile(@NotNull Path file, @Nullable ProgressIndicator indicator) throws IOException;

    @NotNull Path saveToFile(@NotNull Path file, @Nullable ProgressIndicator indicator, boolean progressDescription) throws IOException;

    byte @NotNull [] readBytes(@Nullable ProgressIndicator indicator) throws IOException;

    @NotNull String readString(@Nullable ProgressIndicator indicator) throws IOException;

    default @NotNull String readString() throws IOException {
      return readString(null);
    }

    @NotNull CharSequence readChars(@Nullable ProgressIndicator indicator) throws IOException;

    @Nullable String readError() throws IOException;

    default void write(@NotNull String data) throws IOException {
      write(data.getBytes(StandardCharsets.UTF_8));
    }

    default void write(byte @NotNull [] data) throws IOException {
      var connection = (HttpURLConnection)getConnection();
      connection.setFixedLengthStreamingMode(data.length);
      try (var stream = connection.getOutputStream()) {
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

    public @NotNull String getUrl() {
      return myUrl;
    }

    @Override
    public String toString() {
      return super.toString() + ". Status=" + myStatusCode + ", Url=" + myUrl;
    }
  }

  public static RequestBuilder request(@NotNull Url url) {
    return request(url.toExternalForm());
  }

  public static RequestBuilder request(@NotNull String url) {
    return new RequestBuilderImpl(url, null);
  }

  public static RequestBuilder head(@NotNull String url) {
    return new RequestBuilderImpl(url, connection -> ((HttpURLConnection)connection).setRequestMethod("HEAD"));
  }

  public static RequestBuilder delete(@NotNull String url) {
    return new RequestBuilderImpl(url, connection -> ((HttpURLConnection)connection).setRequestMethod("DELETE"));
  }

  public static RequestBuilder delete(@NotNull String url, @Nullable String contentType) {
    return requestWithBody(url, "DELETE", contentType, null);
  }

  public static RequestBuilder post(@NotNull String url, @Nullable String contentType) {
    return requestWithBody(url, "POST", contentType, null);
  }

  public static RequestBuilder put(@NotNull String url, @Nullable String contentType) {
    return requestWithBody(url, "PUT", contentType, null);
  }

  /**
   * Java does not support "newer" HTTP methods, so we have to rely on server-side support of `X-HTTP-Method-Override` header to invoke PATCH
   * For reasoning see {@link HttpURLConnection#setRequestMethod(String)}
   * <p>
   * TODO: either fiddle with reflection or patch JDK to avoid server reliance
   */
  public static RequestBuilder patch(@NotNull String url, @Nullable String contentType) {
    return requestWithBody(url, "POST", contentType, connection -> connection.setRequestProperty("X-HTTP-Method-Override", "PATCH"));
  }

  public static RequestBuilder requestWithRange(@NotNull String url, @NotNull String bytes) {
    return requestWithBody(url, "GET", null, connection -> connection.setRequestProperty("Range", "bytes=" + bytes));
  }

  private static RequestBuilder requestWithBody(String url, String requestMethod, @Nullable String contentType, @Nullable ConnectionTuner tuner) {
    return new RequestBuilderImpl(url, rawConnection -> {
      var connection = (HttpURLConnection)rawConnection;
      connection.setRequestMethod(requestMethod);
      connection.setDoOutput(true);
      if (contentType != null) connection.setRequestProperty("Content-Type", contentType);
      if (tuner != null) tuner.tune(connection);
    });
  }

  public static @NotNull String createErrorMessage(@NotNull IOException e, @NotNull Request request, boolean includeHeaders) {
    var builder = new StringBuilder();

    builder.append("Cannot download '").append(request.getURL()).append("': ").append(e.getMessage());

    try {
      var connection = request.getConnection();
      if (includeHeaders) {
        builder.append("\n, headers: ").append(connection.getHeaderFields());
      }
      if (connection instanceof HttpURLConnection httpConnection) {
        builder.append("\n, response: ").append(httpConnection.getResponseCode()).append(' ').append(httpConnection.getResponseMessage());
      }
    }
    catch (Throwable ignored) { }

    return builder.toString();
  }

  private static final class RequestBuilderImpl extends RequestBuilder {
    private final String myUrl;
    private int myConnectTimeout = CONNECTION_TIMEOUT;
    private int myTimeout = READ_TIMEOUT;
    private boolean myFollowRedirects = true;
    private int myRedirectLimit = REDIRECT_LIMIT;
    private boolean myGzip = true;
    private boolean myForceHttps;
    private boolean myUseProxy = true;
    private boolean myIsReadResponseOnError;
    private HostnameVerifier myHostnameVerifier;
    private String myUserAgent;
    private String myAccept;
    private ConnectionTuner myTuner;
    private final ConnectionTuner myInternalTuner;
    private boolean myThrowStatusCodeException = true;

    private RequestBuilderImpl(String url, @Nullable ConnectionTuner internalTuner) {
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
    public RequestBuilder followRedirects(boolean value) {
      myFollowRedirects = value;
      return this;
    }

    @Override
    public RequestBuilder redirectLimit(int redirectLimit) {
      if (redirectLimit < 1) {
        throw new IllegalArgumentException("Redirect limit should be positive");
      }
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
      String userAgent;
      var app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        var productName = ApplicationNamesInfo.getInstance().getFullProductName();
        var version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
        userAgent = productName + '/' + version;
      }
      else {
        userAgent = "IntelliJ";
      }

      var currentBuildUrl = System.getenv("BUILD_URL");
      if (currentBuildUrl != null) {
        userAgent += " (" + currentBuildUrl + ")";
      }

      return userAgent(userAgent);
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
    public @NotNull RequestBuilder throwStatusCodeException(boolean shouldThrow) {
      myThrowStatusCodeException = shouldThrow;
      return this;
    }

    @Override
    public <T> T connect(@NotNull HttpRequests.RequestProcessor<T> processor) throws IOException {
      return process(this, processor);
    }
  }

  private static final class RequestImpl implements Request, AutoCloseable {
    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([^;]+)");

    private final RequestBuilderImpl myBuilder;
    private String myUrl;
    private URLConnection myConnection;
    private InputStream myInputStream;
    private BufferedReader myReader;

    private RequestImpl(RequestBuilderImpl builder) {
      myBuilder = builder;
      myUrl = myBuilder.myUrl;
    }

    @Override
    public @NotNull String getURL() {
      return myUrl;
    }

    @Override
    public @NotNull URLConnection getConnection() throws IOException {
      if (myConnection == null) {
        myConnection = openConnection(myBuilder, this);
      }
      return myConnection;
    }

    @Override
    public @NotNull InputStream getInputStream() throws IOException {
      if (myInputStream == null) {
        var connection = getConnection();
        myInputStream = unzipStreamIfNeeded(connection, connection.getInputStream());
      }
      return myInputStream;
    }

    private InputStream unzipStreamIfNeeded(URLConnection connection, InputStream stream) throws IOException {
      return myBuilder.myGzip && "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? CountingGZIPInputStream.create(stream) : stream;
    }

    @Override
    public @NotNull BufferedReader getReader() throws IOException {
      return getReader(null);
    }

    @Override
    public @NotNull BufferedReader getReader(@Nullable ProgressIndicator indicator) throws IOException {
      if (myReader == null) {
        var inputStream = getInputStream();
        if (indicator != null) {
          var contentLength = getConnection().getContentLengthLong();
          if (contentLength > 0) {
            inputStream = new ProgressMonitorInputStream(indicator, inputStream, contentLength);
          }
        }
        myReader = new BufferedReader(new InputStreamReader(inputStream, getCharset()));
      }
      return myReader;
    }

    private Charset getCharset() throws IOException {
      return getCharset(getConnection());
    }

    private static Charset getCharset(URLConnection connection) throws IOException {
      var contentType = connection.getContentType();
      if (contentType != null && !contentType.isEmpty()) {
        var m = CHARSET_PATTERN.matcher(contentType);
        if (m.find()) {
          try {
            return Charset.forName(StringUtil.unquoteString(m.group(1)));
          }
          catch (IllegalArgumentException e) {
            throw new IOException("Unknown charset: " + contentType, e);
          }
        }
      }
      return StandardCharsets.UTF_8;
    }

    @Override
    public byte @NotNull [] readBytes(@Nullable ProgressIndicator indicator) throws IOException {
      return doReadBytes(indicator).toByteArray();
    }

    @Override
    public @NotNull String readString(@Nullable ProgressIndicator indicator) throws IOException {
      var stream = doReadBytes(indicator);
      return stream.size() == 0 ? "" : new String(stream.getInternalBuffer(), 0, stream.size(), getCharset());
    }

    @Override
    public @NotNull CharSequence readChars(@Nullable ProgressIndicator indicator) throws IOException {
      var stream = doReadBytes(indicator);
      return stream.size() == 0 ? "" : getCharset().decode(ByteBuffer.wrap(stream.getInternalBuffer(), 0, stream.size()));
    }

    private BufferExposingByteArrayOutputStream doReadBytes(@Nullable ProgressIndicator indicator) throws IOException {
      var contentLength = getConnection().getContentLengthLong();
      if (contentLength > Integer.MAX_VALUE) throw new IOException("Cannot download more than 2 GiB; content length is " + contentLength);
      var out = new BufferExposingByteArrayOutputStream(contentLength > 0 ? (int)contentLength : 16384);
      NetUtils.copyStreamContent(indicator, getInputStream(), out, contentLength, false);
      return out;
    }

    @Override
    public @Nullable String readError() throws IOException {
      return readError((HttpURLConnection)getConnection());
    }

    private @Nullable String readError(HttpURLConnection connection) throws IOException {
      var stream = connection.getErrorStream();
      return stream == null ? null : StreamUtil.readText(new InputStreamReader(unzipStreamIfNeeded(connection, stream), getCharset(connection)));
    }

    @Override
    public @NotNull Path saveToFile(@NotNull Path file, @Nullable ProgressIndicator indicator) throws IOException {
      return saveToFile(file, indicator, false);
    }

    @Override
    public @NotNull Path saveToFile(@NotNull Path file, @Nullable ProgressIndicator indicator, boolean progressDescription) throws IOException {
      NioFiles.createDirectories(file.getParent());

      var deleteFile = true;
      try (var out = Files.newOutputStream(file)) {
        NetUtils.copyStreamContent(indicator, getInputStream(), out, getConnection().getContentLengthLong(), progressDescription);
        deleteFile = false;
      }
      catch (HttpStatusException e) {
        throw e;
      }
      catch (IOException e) {
        throw new IOException(createErrorMessage(e, this, false), e);
      }
      finally {
        if (deleteFile) {
          Files.deleteIfExists(file);
        }
      }

      return file;
    }

    @Override
    public void close() throws IOException {
      //noinspection EmptyTryBlock
      try (@SuppressWarnings("unused") var s = myInputStream; @SuppressWarnings("unused") Reader r = myReader) { }
      finally {
        if (myConnection instanceof HttpURLConnection) {
          ((HttpURLConnection)myConnection).disconnect();
        }
      }
    }
  }

  private static <T> T process(RequestBuilderImpl builder, RequestProcessor<T> processor) throws IOException {
    var app = ApplicationManager.getApplication();
    LOG.assertTrue(
      app == null || app.isUnitTestMode() || app.isHeadlessEnvironment() || !app.holdsReadLock(),
      "Network must not be accessed in EDT or inside read action, because this may take considerable amount of time, " +
      "and write actions will be blocked during that time so user won't be able to perform tasks in the IDE"
    );
    var contextLoader = Thread.currentThread().getContextClassLoader();
    if (contextLoader != null && shouldOverrideContextClassLoader()) {
      // hack-around for class loader lock in sun.net.www.protocol.http.NegotiateAuthentication (IDEA-131621)
      try (var cl = new URLClassLoader(new URL[0], contextLoader)) {
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
    return Patches.JDK_BUG_ID_8032832 && SystemProperties.getBooleanProperty("http.requests.override.context.classloader", true);
  }

  private static <T> T doProcess(RequestBuilderImpl builder, RequestProcessor<T> processor) throws IOException {
    try (var request = new RequestImpl(builder)) {
      var result = processor.process(request);

      if (builder.myThrowStatusCodeException) {
        var connection = request.myConnection;
        if (connection != null && connection.getDoOutput()) {
          // getResponseCode is not checked on connect, because writing must happen before reading
          var urlConnection = (HttpURLConnection)connection;
          var responseCode = urlConnection.getResponseCode();
          if (responseCode >= 400) {
            throwHttpStatusError(urlConnection, request, builder, responseCode);
          }
        }
      }

      return result;
    }
  }

  private static URLConnection openConnection(RequestBuilderImpl builder, RequestImpl request) throws IOException {
    if (builder.myForceHttps && request.myUrl.startsWith("http:")) {
      request.myUrl = "https:" + request.myUrl.substring(5);
    }

    for (var i = 0; i < builder.myRedirectLimit; i++) {
      var url = request.myUrl;

      URLConnection connection;
      try {
        if (!builder.myUseProxy) {
          connection = new URI(url).toURL().openConnection(Proxy.NO_PROXY);
        }
        else {
          var uiService = getIdeUiService();
          if (uiService != null) {
            connection = uiService.openHttpConnection(url);
          }
          else {
            connection = new URI(url).toURL().openConnection();
          }
        }
      }
      catch (URISyntaxException e) {
        throw new IOException(e);
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

      if (!(connection instanceof HttpURLConnection httpURLConnection)) {
        return connection;
      }

      httpURLConnection.setInstanceFollowRedirects(false);

      if (connection.getDoOutput()) {
        return connection;
      }

      var method = httpURLConnection.getRequestMethod();

      LOG.assertTrue(method.equals("GET") || method.equals("HEAD") || method.equals("DELETE"),
                     "'" + method + "' not supported; please use GET, HEAD, DELETE, PUT or POST");

      if (LOG.isDebugEnabled()) {
        LOG.debug("connecting to " + url);
      }

      int responseCode;
      try {
        responseCode = httpURLConnection.getResponseCode();
      }
      catch (SSLHandshakeException e) {
        throw !NetUtils.isSniEnabled() ? new SSLException("SSL error probably caused by disabled SNI", e) : e;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("response from " + url + ": " + responseCode);
      }

      if (responseCode < 200 || responseCode >= 300 && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
        if (ArrayUtil.indexOf(REDIRECTS, responseCode) >= 0) {
          if (!builder.myFollowRedirects) {
            return connection;
          }

          httpURLConnection.disconnect();
          var loc = connection.getHeaderField("Location");
          if (LOG.isDebugEnabled()) LOG.debug("redirect from " + url + ": " + loc);
          if (loc != null) {
            if (!loc.contains(URLUtil.SCHEME_SEPARATOR)) {
              // location is relative
              try {
                loc = new URI(url).resolve(new URI(loc)).toString();
              }
              catch (URISyntaxException e) {
                throw new IOException(e);
              }
            }
            request.myUrl = loc;
            continue;
          }
        }

        if (builder.myThrowStatusCodeException) {
          throwHttpStatusError(httpURLConnection, request, builder, responseCode);
        }
      }

      return connection;
    }

    throw new IOException(IdeCoreBundle.message("error.connection.failed.redirects"));
  }

  private static void throwHttpStatusError(
    HttpURLConnection connection,
    RequestImpl request,
    RequestBuilderImpl builder,
    int statusCode
  ) throws IOException {
    String message = null;
    if (statusCode == CUSTOM_ERROR_CODE) {
      message = connection.getHeaderField("Error-Message");
    }
    if ((message == null || message.isEmpty()) && builder.myIsReadResponseOnError) {
      message = request.readError(connection);
    }
    if (message == null || message.isEmpty()) {
      message = IdeCoreBundle.message("error.connection.failed.status", statusCode);
    }
    connection.disconnect();
    if (statusCode == HttpURLConnection.HTTP_PROXY_AUTH) {
      var uiService = getIdeUiService();
      if (uiService != null) {
        uiService.showProxyAuthNotification();
      }
      if (LOG.isDebugEnabled()) LOG.debug(
        "proxy auth failed for " + request.getURL() + "; Proxy-Authenticate=" + connection.getHeaderField("Proxy-Authenticate"),
        new Exception()
      );
    }
    throw new HttpStatusException(message, statusCode, requireNonNullElse(request.myUrl, "Empty URL"));
  }

  private static void configureSslConnection(String url, HttpsURLConnection connection) {
    var uiService = getIdeUiService();
    if (uiService == null) {
      LOG.info("Application is not initialized yet; Using default SSL configuration to connect to " + url);
      return;
    }

    try {
      var factory = uiService.getSslSocketFactory();
      if (factory == null) {
        LOG.info("SSLSocketFactory is not defined by the IDE Certificate Manager; Using default SSL configuration to connect to " + url);
      }
      else {
        connection.setSSLSocketFactory(factory);
      }
    }
    catch (Throwable e) {
      LOG.info("Problems configuring SSL connection to " + url, e);
    }
  }

  private static @Nullable IdeUiService getIdeUiService() {
    var app = ApplicationManager.getApplication();
    return app != null ? app.getServiceIfCreated(IdeUiService.class) : null;
  }

  /*
   * Many servers would not process a request and just return 400 (Bad Request) response if any of request headers contains NUL byte.
   * This method checks the request and removes invalid headers.
   */
  private static void checkRequestHeadersForNulBytes(URLConnection connection) {
    for (var header : connection.getRequestProperties().entrySet()) {
      for (var headerValue : header.getValue()) {
        if (headerValue.indexOf('\0') >= 0) {
          connection.setRequestProperty(header.getKey(), null);
          LOG.error(String.format(
            "Problem during request to '%s'. Header's '%s' value contains NUL bytes: '%s'. Omitting this header.",
            connection.getURL().toString(), header.getKey(), headerValue
          ));
          break;
        }
      }
    }
  }
}
