/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
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
  private static final boolean ourWrapClassLoader =
    SystemInfo.isJavaVersionAtLeast("1.7") && !SystemProperties.getBooleanProperty("idea.parallel.class.loader", true);

  public interface Request {
    @NotNull URLConnection getConnection() throws IOException;
    @NotNull InputStream getInputStream() throws IOException;
  }

  public interface RequestProcessor<T> {
    T process(@NotNull Request request) throws IOException;
  }

  public static class RequestBuilder {
    private final String myUrl;
    private int myConnectTimeout = HttpConfigurable.CONNECTION_TIMEOUT;
    private int myTimeout = HttpConfigurable.READ_TIMEOUT;
    private int myRedirectLimit = HttpConfigurable.REDIRECT_LIMIT;
    private boolean myGzip = true;
    private boolean myForceHttps;
    private HostnameVerifier myHostnameVerifier;
    private String myUserAgent;

    private RequestBuilder(@NotNull String url) {
      myUrl = url;
    }

    @NotNull
    public RequestBuilder connectTimeout(int value) {
      myConnectTimeout = value;
      return this;
    }

    @NotNull
    public RequestBuilder readTimeout(int value) {
      myTimeout = value;
      return this;
    }

    @NotNull
    public RequestBuilder redirectLimit(int redirectLimit) {
      myRedirectLimit = redirectLimit;
      return this;
    }

    @NotNull
    public RequestBuilder gzip(boolean value) {
      myGzip = value;
      return this;
    }

    @NotNull
    public RequestBuilder forceHttps(boolean forceHttps) {
      myForceHttps = forceHttps;
      return this;
    }

    @NotNull
    public RequestBuilder hostNameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
      myHostnameVerifier = hostnameVerifier;
      return this;
    }

    @NotNull
    public RequestBuilder userAgent(@Nullable String userAgent) {
      myUserAgent = userAgent;
      return this;
    }

    public <T> T connect(@NotNull RequestProcessor<T> processor) throws IOException {
      // todo[r.sh] drop condition in IDEA 15
      if (ourWrapClassLoader) {
        return wrapAndProcess(this, processor);
      }
      else {
        return process(this, processor);
      }
    }
  }

  @NotNull
  public static RequestBuilder request(@NotNull String url) {
    return new RequestBuilder(url);
  }

  private static <T> T wrapAndProcess(RequestBuilder builder, RequestProcessor<T> processor) throws IOException {
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

  private static <T> T process(final RequestBuilder builder, RequestProcessor<T> processor) throws IOException {
    class RequestImpl implements Request {
      private URLConnection myConnection;
      private InputStream myInputStream;

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

      private void cleanup() throws IOException {
        if (myInputStream != null) {
          myInputStream.close();
        }
        if (myConnection instanceof HttpURLConnection) {
          ((HttpURLConnection)myConnection).disconnect();
        }
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

  private static URLConnection openConnection(RequestBuilder builder) throws IOException {
    String url = builder.myUrl;

    if (builder.myForceHttps && StringUtil.startsWith(url, "http:")) {
      url = "https:" + url.substring(5);
    }

    for (int i = 0; i < builder.myRedirectLimit; i++) {
      URLConnection connection;
      if (ApplicationManager.getApplication() == null) {
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

      if (builder.myGzip) {
        connection.setRequestProperty("Accept-Encoding", "gzip");
      }
      connection.setUseCaches(false);

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

          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
        }
      }

      return connection;
    }

    throw new IOException(IdeBundle.message("error.connection.failed.redirects"));
  }
}