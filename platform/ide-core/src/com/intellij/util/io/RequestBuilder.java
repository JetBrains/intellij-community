// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.nio.file.Path;

@SuppressWarnings("BoundedWildcard")
public abstract class RequestBuilder {
  public abstract RequestBuilder connectTimeout(int value);
  public abstract RequestBuilder readTimeout(int value);
  public abstract RequestBuilder redirectLimit(int redirectLimit);

  /**
   * Whether gzip encoding supported. Defaults to {@code true}.
   */
  public abstract RequestBuilder gzip(boolean value);

  public abstract RequestBuilder forceHttps(boolean forceHttps);
  public abstract RequestBuilder useProxy(boolean useProxy);
  public abstract RequestBuilder hostNameVerifier(@Nullable HostnameVerifier hostnameVerifier);
  public abstract RequestBuilder userAgent(@Nullable String userAgent);
  public abstract RequestBuilder productNameAsUserAgent();
  public abstract RequestBuilder accept(@Nullable String mimeType);
  public abstract RequestBuilder tuner(@Nullable HttpRequests.ConnectionTuner tuner);

  /**
   * Whether to read server response on error. Error message available as {@link HttpRequests.HttpStatusException#getMessage()}.
   * Defaults to false.
   */
  public abstract RequestBuilder isReadResponseOnError(boolean isReadResponseOnError);

  /**
   * Whether to analyze response status code and throw an exception if it's an "error" code.
   * Defaults to true.
   */
  public abstract @NotNull RequestBuilder throwStatusCodeException(boolean shouldThrow);

  public abstract <T> T connect(@NotNull HttpRequests.RequestProcessor<T> processor) throws IOException;

  public int tryConnect() throws IOException {
    return connect(request -> {
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
    connect(request -> request.saveToFile(file, indicator));
  }

  public void saveToFile(@NotNull Path file, @Nullable ProgressIndicator indicator) throws IOException {
    connect(request -> request.saveToFile(file, indicator));
  }

  public void saveToFile(@NotNull Path file, @Nullable ProgressIndicator indicator, boolean progressDescription) throws IOException {
    connect(request -> request.saveToFile(file, indicator, progressDescription));
  }

  public byte @NotNull [] readBytes(@Nullable ProgressIndicator indicator) throws IOException {
    return connect(request -> request.readBytes(indicator));
  }

  public @NotNull String readString(@Nullable ProgressIndicator indicator) throws IOException {
    return connect(request -> request.readString(indicator));
  }

  public @NotNull String readString() throws IOException {
    return readString(null);
  }

  public @NotNull CharSequence readChars(@Nullable ProgressIndicator indicator) throws IOException {
    return connect(request -> request.readChars(indicator));
  }

  public @NotNull CharSequence readChars() throws IOException {
    return readChars(null);
  }

  public void write(@NotNull String data) throws IOException {
    connect(request -> {
      request.write(data);
      return null;
    });
  }

  public void write(byte @NotNull [] data) throws IOException {
    connect(request -> {
      request.write(data);
      return null;
    });
  }
}
