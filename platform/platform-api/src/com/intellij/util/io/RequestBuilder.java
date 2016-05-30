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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.SystemProperties;
import com.intellij.util.net.HTTPMethod;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

public final class RequestBuilder {
  private static final boolean ourWrapClassLoader =
    SystemInfo.isJavaVersionAtLeast("1.7") && !SystemProperties.getBooleanProperty("idea.parallel.class.loader", true);

  final String myUrl;
  int myConnectTimeout = HttpConfigurable.CONNECTION_TIMEOUT;
  int myTimeout = HttpConfigurable.READ_TIMEOUT;
  int myRedirectLimit = HttpConfigurable.REDIRECT_LIMIT;
  boolean myGzip = true;
  boolean myForceHttps;
  boolean myUseProxy = true;
  HostnameVerifier myHostnameVerifier;
  String myUserAgent;
  String myAccept;
  HttpRequests.ConnectionTuner myTuner;

  HTTPMethod myMethod;

  RequestBuilder(@NotNull String url) {
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
  public RequestBuilder useProxy(boolean useProxy) {
    myUseProxy = useProxy;
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
  
  public RequestBuilder tuner(@Nullable HttpRequests.ConnectionTuner tuner) {
    myTuner = tuner;
    return this;
  }

  @NotNull
  public RequestBuilder productNameAsUserAgent() {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      ApplicationInfo info = ApplicationInfo.getInstance();
      return userAgent(info.getVersionName() + "/" + info.getBuild().asStringWithoutProductCode());
    }
    else {
      return userAgent("IntelliJ");
    }
  }

  @NotNull
  public RequestBuilder accept(@Nullable String mimeType) {
    myAccept = mimeType;
    return this;
  }

  public <T> T connect(@NotNull HttpRequests.RequestProcessor<T> processor) throws IOException {
    // todo[r.sh] drop condition in IDEA 15
    if (ourWrapClassLoader) {
      return HttpRequests.wrapAndProcess(this, processor);
    }
    else {
      return HttpRequests.process(this, processor);
    }
  }

  public int tryConnect() throws IOException {
    HttpRequests.RequestProcessor<Integer> processor = new HttpRequests.RequestProcessor<Integer>() {
      @Override
      public Integer process(@NotNull HttpRequests.Request request) throws IOException {
        URLConnection connection = request.getConnection();
        return connection instanceof HttpURLConnection ? ((HttpURLConnection)connection).getResponseCode() : -1;
      }
    };

    if (ourWrapClassLoader) {
      return HttpRequests.wrapAndProcess(this, processor);
    }
    else {
      return HttpRequests.process(this, processor);
    }
  }

  public <T> T connect(@NotNull HttpRequests.RequestProcessor<T> processor, T errorValue, @Nullable Logger logger) {
    try {
      return connect(processor);
    }
    catch (Throwable e) {
      if (logger != null) {
        logger.warn(e);
      }
      return errorValue;
    }
  }

  public void saveToFile(@NotNull final File file, @Nullable final ProgressIndicator indicator) throws IOException {
    connect(new HttpRequests.RequestProcessor<Void>() {
      @Override
      public Void process(@NotNull HttpRequests.Request request) throws IOException {
        request.saveToFile(file, indicator);
        return null;
      }
    });
  }

  @NotNull
  public byte[] readBytes(@Nullable final ProgressIndicator indicator) throws IOException {
    return connect(new HttpRequests.RequestProcessor<byte[]>() {
      @Override
      public byte[] process(@NotNull HttpRequests.Request request) throws IOException {
        return request.readBytes(indicator);
      }
    });
  }

  @NotNull
  public String readString(@Nullable final ProgressIndicator indicator) throws IOException {
    return connect(new HttpRequests.RequestProcessor<String>() {
      @Override
      public String process(@NotNull HttpRequests.Request request) throws IOException {
        int contentLength = request.getConnection().getContentLength();
        BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream(contentLength > 0 ? contentLength : 16 * 1024);
        NetUtils.copyStreamContent(indicator, request.getInputStream(), out, contentLength);
        return new String(out.getInternalBuffer(), 0, out.size(), HttpRequests.getCharset(request));
      }
    });
  }
}
