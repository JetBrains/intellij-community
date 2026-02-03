// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.Map;

public final class ExternalAppUtil {

  private ExternalAppUtil() { }

  public static @NotNull Result sendIdeRequest(@NotNull String entryPoint, int idePort, @NotNull String handlerId, @Nullable String bodyContent) {
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

  /**
   * @deprecated use the version with ExternalAppEntry
   */
  @Deprecated(since = "2025.2", forRemoval = true)
  public static @NotNull String getEnv(@NotNull String env) {
    String value = System.getenv(env);
    if (value == null) {
      throw new IllegalStateException(env + " environment variable is not defined!");
    }
    return value;
  }

  public static @NotNull String getEnv(@NotNull String env, @NotNull Map<String, String> environment) {
    String value = environment.get(env);
    if (value == null) {
      throw new IllegalStateException(env + " environment variable is not defined!");
    }
    return value;
  }

  /**
   * @deprecated use the version with ExternalAppEntry
   */
  @Deprecated(since = "2025.2", forRemoval = true)
  public static int getEnvInt(@NotNull String env) {
    return Integer.parseInt(getEnv(env));
  }

  public static int getEnvInt(@NotNull String env, @NotNull Map<String, String> environment) {
    return Integer.parseInt(getEnv(env, environment));
  }

  /**
   * @deprecated use the version with ExternalAppEntry
   */
  @Deprecated(since = "2025.2", forRemoval = true)
  public static void handleAskPassInvocation(@NotNull String handlerIdEnvName,
                                             @NotNull String idePortEnvName,
                                             @NotNull String entryPoint,
                                             String[] args) {
    var exitCode = handleAskPassInvocation(handlerIdEnvName,
                                           idePortEnvName,
                                           entryPoint,
                                           ExternalAppEntry.fromMain(args));
    System.exit(exitCode);
  }

  public static int handleAskPassInvocation(@NotNull String handlerIdEnvName,
                                             @NotNull String idePortEnvName,
                                             @NotNull String entryPoint,
                                             ExternalAppEntry entry) {
    try {
      var args = entry.getArgs();
      String handlerId = getEnv(handlerIdEnvName, entry.getEnvironment());
      int idePort = getEnvInt(idePortEnvName, entry.getEnvironment());

      String description = args.length > 0 ? args[0] : null;

      ExternalAppUtil.Result result = sendIdeRequest(entryPoint, idePort, handlerId, description);

      if (result.isError) {
        entry.getStderr().println(result.getPresentableError());
        return 1;
      }

      String passphrase = result.response;
      if (passphrase == null) {
        entry.getStderr().println("Authentication request was cancelled");
        return 1; // dialog canceled
      }

      entry.getStdout().println(passphrase);
      return 0;
    }
    catch (Throwable t) {
      entry.getStderr().println(t.getMessage());
      t.printStackTrace(entry.getStderr());
      return 1;
    }
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
