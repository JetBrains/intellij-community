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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

public abstract class RequestBuilder {
  public abstract RequestBuilder connectTimeout(int value);
  public abstract RequestBuilder readTimeout(int value);
  public abstract RequestBuilder redirectLimit(int redirectLimit);
  public abstract RequestBuilder gzip(boolean value);
  public abstract RequestBuilder forceHttps(boolean forceHttps);
  public abstract RequestBuilder useProxy(boolean useProxy);
  public abstract RequestBuilder hostNameVerifier(@Nullable HostnameVerifier hostnameVerifier);
  public abstract RequestBuilder userAgent(@Nullable String userAgent);
  public abstract RequestBuilder productNameAsUserAgent();
  public abstract RequestBuilder accept(@Nullable String mimeType);
  public abstract RequestBuilder tuner(@Nullable HttpRequests.ConnectionTuner tuner);

  public abstract <T> T connect(@NotNull HttpRequests.RequestProcessor<T> processor) throws IOException;

  public int tryConnect() throws IOException {
    return connect((request) -> {
      URLConnection connection = request.getConnection();
      return connection instanceof HttpURLConnection ? ((HttpURLConnection)connection).getResponseCode() : -1;
    });
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

  public void saveToFile(@NotNull File file, @Nullable ProgressIndicator indicator) throws IOException {
    connect((request) -> request.saveToFile(file, indicator));
  }

  @NotNull
  public byte[] readBytes(@Nullable ProgressIndicator indicator) throws IOException {
    return connect((request) -> request.readBytes(indicator));
  }

  @NotNull
  public String readString(@Nullable ProgressIndicator indicator) throws IOException {
    return connect((request) -> request.readString(indicator));
  }
}