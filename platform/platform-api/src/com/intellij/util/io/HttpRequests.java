/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.net.HTTPMethod;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;

/**
 * Handy class for reading data from HTTP connections with built-in support for HTTP redirects and gzipped content and automatic cleanup.
 * Usage: <pre>{@code
 * int firstByte = HttpRequests.request(url).connect(new HttpRequests.RequestProcessor<Integer>() {
 *   public Integer process(@NotNull Request request) throws IOException {
 *     return request.getInputStream().read();
 *   }
 * });
 * }</pre>
 */
public final class HttpRequests {
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

    /**
     * @deprecated Called automatically on open connection. Use {@link RequestBuilder#tryConnect()} to get response code.
     **/
    boolean isSuccessful() throws IOException;

    @NotNull
    File saveToFile(@NotNull File file, @Nullable ProgressIndicator indicator) throws IOException;

    byte[] readBytes(@Nullable ProgressIndicator indicator) throws IOException;
  }

  public interface ConnectionTuner {
    void tune(@NotNull URLConnection connection) throws IOException;
  }

  public interface RequestProcessor<T> {
    T process(@NotNull Request request) throws IOException;
  }

  @NotNull
  public static RequestBuilder request(@NotNull String url) {
    return new RequestBuilder(url);
  }

  @NotNull
  public static RequestBuilder head(@NotNull String url) {
    RequestBuilder builder = request(url);
    builder.myMethod = HTTPMethod.HEAD;
    return builder;
  }

  @NotNull
  public static String createErrorMessage(@NotNull IOException e, @NotNull Request request, boolean includeHeaders) throws IOException {
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

  static <T> T wrapAndProcess(RequestBuilder builder, RequestProcessor<T> processor) throws IOException {
    // hack-around for class loader lock in sun.net.www.protocol.http.NegotiateAuthentication (IDEA-131621)
    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], oldClassLoader));
    try {
      return process(builder, processor);
    }
    finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  @NotNull
  static Charset getCharset(@NotNull Request request) throws IOException {
    String contentEncoding = request.getConnection().getContentEncoding();
    if (contentEncoding != null) {
      try {
        return Charset.forName(contentEncoding);
      }
      catch (Exception ignored) {
      }
    }
    return CharsetToolkit.UTF8_CHARSET;
  }

  static <T> T process(@NotNull final RequestBuilder builder, @NotNull RequestProcessor<T> processor) throws IOException {
    class RequestImpl implements Request {
      private URLConnection myConnection;
      private InputStream myInputStream;
      private BufferedReader myReader;

      @NotNull
      @Override
      public String getURL() {
        return builder.myUrl;
      }

      @NotNull
      @Override
      public URLConnection getConnection() throws IOException {
        if (myConnection == null) {
          myConnection = openConnection(builder);
        }
        return myConnection;
      }

      @NotNull
      @Override
      public InputStream getInputStream() throws IOException {
        if (myInputStream == null) {
          myInputStream = getConnection().getInputStream();
          if (builder.myGzip && "gzip".equalsIgnoreCase(getConnection().getContentEncoding())) {
            //noinspection IOResourceOpenedButNotSafelyClosed
            myInputStream = new GZIPInputStream(myInputStream);
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

      private void cleanup() {
        StreamUtil.closeStream(myInputStream);
        StreamUtil.closeStream(myReader);
        if (myConnection instanceof HttpURLConnection) {
          ((HttpURLConnection)myConnection).disconnect();
        }
      }

      @Override
      @NotNull
      public byte[] readBytes(@Nullable ProgressIndicator indicator) throws IOException {
        int contentLength = getConnection().getContentLength();
        BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(contentLength > 0 ? contentLength : 32 * 1024);
        NetUtils.copyStreamContent(indicator, getInputStream(), out, contentLength);
        return ArrayUtil.realloc(out.getInternalBuffer(), out.size());
      }

      @Override
      @NotNull
      public File saveToFile(@NotNull File file, @Nullable ProgressIndicator indicator) throws IOException {
        FileUtilRt.createParentDirs(file);

        boolean deleteFile = true;
        try {
          OutputStream out = new FileOutputStream(file);
          try {
            NetUtils.copyStreamContent(indicator, getInputStream(), out, getConnection().getContentLength());
            deleteFile = false;
          }
          catch (IOException e) {
            throw new IOException(createErrorMessage(e, this, false), e);
          }
          finally {
            out.close();
          }
        }
        finally {
          if (deleteFile) {
            FileUtilRt.delete(file);
          }
        }

        return file;
      }
    }

    RequestImpl request = new RequestImpl();
    try {
      return processor.process(request);
    }
    finally {
      request.cleanup();
    }
  }

  @NotNull
  private static URLConnection openConnection(RequestBuilder builder) throws IOException {
    String url = builder.myUrl;

    for (int i = 0; i < builder.myRedirectLimit; i++) {
      if (builder.myForceHttps && StringUtil.startsWith(url, "http:")) {
        url = "https:" + url.substring(5);
      }

      URLConnection connection;
      if (!builder.myUseProxy) {
        connection = new URL(url).openConnection(Proxy.NO_PROXY);
      }
      else if (ApplicationManager.getApplication() == null) {
        connection = new URL(url).openConnection();
      }
      else {
        connection = HttpConfigurable.getInstance().openConnection(url);
      }

      connection.setConnectTimeout(builder.myConnectTimeout);
      connection.setReadTimeout(builder.myTimeout);

      if (builder.myUserAgent != null) {
        connection.setRequestProperty("User-Agent", builder.myUserAgent);
      }

      if (builder.myHostnameVerifier != null && connection instanceof HttpsURLConnection) {
        ((HttpsURLConnection)connection).setHostnameVerifier(builder.myHostnameVerifier);
      }

      if (builder.myMethod != null) {
        ((HttpURLConnection)connection).setRequestMethod(builder.myMethod.name());
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
        int responseCode = ((HttpURLConnection)connection).getResponseCode();

        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED) {
          ((HttpURLConnection)connection).disconnect();

          if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            url = connection.getHeaderField("Location");
            if (url != null) {
              continue;
            }
          }
          throw new HttpStatusException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode), responseCode,
                                        StringUtil.notNullize(url, "Empty URL"));
        }
      }

      return connection;
    }

    throw new IOException(IdeBundle.message("error.connection.failed.redirects"));
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
    public String toString() {
      return super.toString() + ". Status=" + myStatusCode + ", Url=" + myUrl;
    }
  }
}
