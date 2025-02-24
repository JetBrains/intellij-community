// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.HttpRequests.HttpStatusException;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Collection of helpers for working with {@link HttpClient}.
 * <p>
 * Example:
 * <pre>
 *   var client = PlatformHttpClient.client();
 *   var request = PlatformHttpClient.request(uri);
 *   var response = PlatformHttpClient.checkResponse(client.send(request, HttpResponse.BodyHandlers.ofString()));
 *   var content = response.body();
 * </pre>
 *
 * @since 2025.2
 */
@ApiStatus.Experimental
public final class PlatformHttpClient {
  /**
   * Returns a preconfigured {@link HttpClient}. For more customization, use {@link #clientBuilder()}.
   */
  public static @NotNull HttpClient client() {
    return clientBuilder().build();
  }

  /**
   * Returns a preconfigured {@link HttpClient.Builder}.
   */
  public static HttpClient.@NotNull Builder clientBuilder() {
    return HttpClient.newBuilder()
      .executor(ExecutorsKt.asExecutor(Dispatchers.getIO()))
      .connectTimeout(Duration.ofMillis(HttpRequests.CONNECTION_TIMEOUT))
      .followRedirects(HttpClient.Redirect.NORMAL);
  }

  /**
   * Uses the given URI to construct a preconfigured {@link HttpRequest}. For more customization, use {@link #requestBuilder(URI)}.
   */
  public static HttpRequest request(@NotNull URI uri) {
    return requestBuilder(uri).build();
  }

  /**
   * Uses the given URI to construct a preconfigured {@link HttpRequest.Builder}.
   */
  public static HttpRequest.@NotNull Builder requestBuilder(@NotNull URI uri) {
    return HttpRequest.newBuilder(uri)
      .timeout(Duration.ofMillis(HttpRequests.READ_TIMEOUT))
      .header("User-Agent", userAgent());
  }

  public static @NotNull String userAgent() {
    var app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      var productName = ApplicationNamesInfo.getInstance().getFullProductName();
      var version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
      return productName + '/' + version;
    }
    else {
      return "IntelliJ";
    }
  }

  /**
   * Throws {@link HttpStatusException} if a response status code is not the {@code [200, 300)} range.
   */
  public static <T> HttpResponse<T> checkResponse(@NotNull HttpResponse<T> response) throws HttpStatusException {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new HttpStatusException(errorMessage(response), response.statusCode(), response.uri().toString());
    }
    return response;
  }

  private static String errorMessage(HttpResponse<?> response) {
    String message = null;
    if (response.statusCode() == HttpRequests.CUSTOM_ERROR_CODE) {
      message = response.headers().firstValue("Error-Message").orElse(null);
    }
    if (message == null) {
      message = IdeCoreBundle.message("error.connection.failed.status", response.statusCode());
    }
    return message;
  }
}
