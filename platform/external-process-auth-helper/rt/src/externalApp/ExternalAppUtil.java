// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package externalApp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;

public final class ExternalAppUtil {
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @NotNull
  public static Result sendIdeRequest(@NotNull String entryPoint, int idePort, @NotNull String handlerId, @Nullable String bodyContent) {
    try {
      // allow self-signed certificates of IDE
      TrustManager[] tm = {new AllowingTrustManager()};
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, tm, null);

      HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .sslContext(sslContext)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
        .uri(URI.create(String.format("https://127.0.0.1:%s/api/%s?%s=%s", idePort, entryPoint,
                                      ExternalAppHandler.HANDLER_ID_PARAMETER, handlerId)));
      if (bodyContent != null) {
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyContent));
      }
      else {
        requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
      }

      HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      int statusCode = response.statusCode();
      if (statusCode == HttpURLConnection.HTTP_OK) {
        return Result.success(statusCode, response.body());
      }
      else if (statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
        return Result.success(statusCode, null);
      }
      else {
        return Result.error(statusCode, response.body());
      }
    }
    catch (IOException | InterruptedException | NoSuchAlgorithmException | KeyManagementException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static String getEnv(@NotNull String env) {
    String value = System.getenv(env);
    if (value == null) {
      throw new IllegalStateException(env + " environment variable is not defined!");
    }
    return value;
  }

  public static int getEnvInt(@NotNull String env) {
    return Integer.parseInt(getEnv(env));
  }

  private static class AllowingTrustManager extends X509ExtendedTrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return null;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
    }
  }

  public static class Result {
    public final boolean isError;
    public final int statusCode;
    public final String response;
    public final String error;

    private Result(int statusCode, String response, String error, boolean isError) {
      this.statusCode = statusCode;
      this.response = response;
      this.error = error;
      this.isError = isError;
    }

    public static Result success(int statusCode, @Nullable String response) {
      return new Result(statusCode, response, null, false);
    }

    public static Result error(int statusCode, @Nullable String error) {
      return new Result(statusCode, null, error, true);
    }

    public @NotNull String getPresentableError() {
      String msg = "Could not communicate with IDE: " + statusCode;
      if (error != null && !error.isEmpty()) {
        msg += " - " + error;
      }
      return msg;
    }
  }
}
