// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.HttpRequests.HttpStatusException;
import com.intellij.util.net.ssl.CertificateManager;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

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
 * <p>
 * Notable differences with {@link HttpRequests}:
 * <ul>
 *   <li>No default read timeout. Clients should use {@link HttpClient#sendAsync} instead.</li>
 *   <li>No transparent GZIP handling. Clients should decode raw bytes with {@link GZIPInputStream} or use {@link #gzipStringBodyHandler}.</li>
 * </ul>
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
    var builder = new DelegatingHttpClientBuilder()
      .executor(ExecutorsKt.asExecutor(Dispatchers.getIO()))
      .connectTimeout(Duration.ofMillis(HttpRequests.CONNECTION_TIMEOUT))
      .followRedirects(HttpClient.Redirect.NORMAL);
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      builder = builder.sslContext(CertificateManager.getInstance().getSslContext());
    }
    return builder;
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
    return (uri.getScheme().equals("file") ? new FileRequestBuilder().uri(uri) : HttpRequest.newBuilder(uri))
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

  public static HttpResponse.BodyHandler<String> gzipStringBodyHandler() {
    return responseInfo -> {
      var isGzip = responseInfo.headers().firstValue("Content-Encoding").map(v -> "gzip".equalsIgnoreCase(v)).orElse(false);
      var charset = findCharset(responseInfo.headers());

      if (!isGzip) {
        return HttpResponse.BodySubscribers.ofString(charset);
      }

      return new HttpResponse.BodySubscriber<>() {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
          delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
          delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
          delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
          delegate.onComplete();
        }

        @Override
        public CompletionStage<String> getBody() {
          return delegate.getBody().thenApply(bytes -> {
            try (var stream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
              return new String(stream.readAllBytes(), charset);
            }
            catch (IOException e) {
              throw new CompletionException(e);
            }
          });
        }
      };
    };
  }

  private static Charset findCharset(HttpHeaders headers) {
    return headers.firstValue("Content-Type").map(v -> {
      int p = v.indexOf("charset=");
      if (p > 0) {
        try {
          return Charset.forName(v.substring(p + 8).trim());
        }
        catch (Exception ignored) { }
      }
      return null;
    }).orElse(StandardCharsets.UTF_8);
  }

  //<editor-fold desc="Delegating HTTP client">
  private static final class DelegatingHttpClientBuilder implements HttpClient.Builder {
    private final HttpClient.Builder delegate = HttpClient.newBuilder();

    @Override
    public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
      delegate.cookieHandler(cookieHandler);
      return this;
    }

    @Override
    public HttpClient.Builder connectTimeout(Duration duration) {
      delegate.connectTimeout(duration);
      return this;
    }

    @Override
    public HttpClient.Builder sslContext(SSLContext sslContext) {
      delegate.sslContext(sslContext);
      return this;
    }

    @Override
    public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
      delegate.sslParameters(sslParameters);
      return this;
    }

    @Override
    public HttpClient.Builder executor(Executor executor) {
      delegate.executor(executor);
      return this;
    }

    @Override
    public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
      delegate.followRedirects(policy);
      return this;
    }

    @Override
    public HttpClient.Builder version(HttpClient.Version version) {
      delegate.version(version);
      return this;
    }

    @Override
    public HttpClient.Builder priority(int priority) {
      delegate.priority(priority);
      return this;
    }

    @Override
    public HttpClient.Builder proxy(ProxySelector proxySelector) {
      delegate.proxy(proxySelector);
      return this;
    }

    @Override
    public HttpClient.Builder authenticator(Authenticator authenticator) {
      delegate.authenticator(authenticator);
      return this;
    }

    @Override
    public HttpClient build() {
      return new DelegatingHttpClient(delegate.build());
    }
  }

  private static final class DelegatingHttpClient extends HttpClient {
    private static final Flow.Subscription NULL_SUBSCRIPTION = new Flow.Subscription() {
      @Override
      public void request(long n) { }

      @Override
      public void cancel() { }
    };

    private final HttpClient delegate;

    private DelegatingHttpClient(HttpClient delegate) { this.delegate = delegate; }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
      return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
      return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
      return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return delegate.authenticator();
    }

    @Override
    public Version version() {
      return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
      return delegate.executor();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) throws IOException, InterruptedException {
      if (request instanceof FileHttpRequest) {
        try {
          return sendAsync(request, responseHandler).get();
        }
        catch (ExecutionException e) {
          var cause = e.getCause();
          if (cause instanceof IOException ioe) throw ioe;
          throw new RuntimeException(cause);
        }
      }
      else {
        return delegate.send(request, responseHandler);
      }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) {
      return sendAsync(request, responseHandler, null);
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request,
      HttpResponse.BodyHandler<T> responseHandler,
      HttpResponse.PushPromiseHandler<T> pushPromiseHandler
    ) {
      if (request instanceof FileHttpRequest fhr) {
        var result = new CompletableFuture<HttpResponse<T>>();
        delegate.executor().orElseGet(() -> ExecutorsKt.asExecutor(Dispatchers.getIO())).execute(() -> {
          try {
            var data = Files.readAllBytes(Path.of(request.uri()));
            completeResult(responseHandler, fhr, data, result);
          }
          catch (NoSuchFileException e) {
            completeResult(responseHandler, fhr, null, result);
          }
          catch (InvalidPathException e) {
            result.completeExceptionally(new IOException(e));
          }
          catch (IOException e) {
            result.completeExceptionally(e);
          }
        });
        return result;
      }
      else {
        return delegate.sendAsync(request, responseHandler, pushPromiseHandler);
      }
    }

    private static <T> void completeResult(
      HttpResponse.BodyHandler<T> responseHandler,
      FileHttpRequest request,
      byte @Nullable [] data,
      CompletableFuture<HttpResponse<T>> result
    ) {
      var responseInfo = new FileResponseInfo(data != null ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_NOT_FOUND);
      var subscriber = responseHandler.apply(responseInfo);
      subscriber.onSubscribe(NULL_SUBSCRIPTION);
      if (data != null) {
        subscriber.onNext(List.of(ByteBuffer.wrap(data)));
      }
      subscriber.onComplete();
      subscriber.getBody().whenComplete((body, ex) -> {
        if (ex != null) {
          result.completeExceptionally(ex);
        } else {
          result.complete(new FileHttpResponse<>(request, responseInfo, body));
        }
      });
    }
  }
  //</editor-fold>

  //<editor-fold desc="File requests implementation">
  private static class FileRequestBuilder implements HttpRequest.Builder {
    private URI uri;

    @Override
    public HttpRequest.Builder uri(@NotNull URI uri) {
      this.uri = uri;
      return this;
    }

    @Override
    public HttpRequest.Builder expectContinue(boolean enable) {
      return this;
    }

    @Override
    public HttpRequest.Builder version(HttpClient.Version version) {
      return this;
    }

    @Override
    public HttpRequest.Builder header(String name, String value) {
      return this;
    }

    @Override
    public HttpRequest.Builder headers(String... headers) {
      return this;
    }

    @Override
    public HttpRequest.Builder timeout(Duration duration) {
      return this;
    }

    @Override
    public HttpRequest.Builder setHeader(String name, String value) {
      return this;
    }

    @Override
    public HttpRequest.Builder GET() {
      return this;
    }

    @Override
    public HttpRequest.Builder POST(HttpRequest.BodyPublisher bodyPublisher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public HttpRequest.Builder PUT(HttpRequest.BodyPublisher bodyPublisher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public HttpRequest.Builder DELETE() {
      throw new UnsupportedOperationException();
    }

    @Override
    public HttpRequest.Builder method(String method, HttpRequest.BodyPublisher bodyPublisher) {
      throw new UnsupportedOperationException();
    }

    @Override
    public HttpRequest build() {
      return new FileHttpRequest(uri);
    }

    @Override
    public HttpRequest.Builder copy() {
      return new FileRequestBuilder().uri(uri);
    }
  }

  private static final class FileHttpRequest extends HttpRequest {
    private static final HttpHeaders EMPTY_HEADERS = HttpHeaders.of(Map.of(), (name, value) -> true);

    private final URI uri;

    private FileHttpRequest(URI uri) {
      this.uri = uri;
    }

    @Override
    public Optional<BodyPublisher> bodyPublisher() {
      return Optional.empty();
    }

    @Override
    public String method() {
      return "GET";
    }

    @Override
    public Optional<Duration> timeout() {
      return Optional.empty();
    }

    @Override
    public boolean expectContinue() {
      return false;
    }

    @Override
    public URI uri() {
      return uri;
    }

    @Override
    public Optional<HttpClient.Version> version() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return EMPTY_HEADERS;
    }
  }

  private static final class FileResponseInfo implements HttpResponse.ResponseInfo {
    private final int responseCode;

    private FileResponseInfo(int responseCode) {
      this.responseCode = responseCode;
    }

    @Override
    public int statusCode() {
      return responseCode;
    }

    @Override
    public HttpHeaders headers() {
      return FileHttpRequest.EMPTY_HEADERS;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final class FileHttpResponse<T> implements HttpResponse<T> {
    private final FileHttpRequest request;
    private final FileResponseInfo responseInfo;
    private final T body;

    private FileHttpResponse(FileHttpRequest request, FileResponseInfo responseInfo, T body) {
      this.request = request;
      this.responseInfo = responseInfo;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return responseInfo.statusCode();
    }

    @Override
    public HttpRequest request() {
      return request;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return responseInfo.headers();
    }

    @Override
    public T body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return request().uri();
    }

    @Override
    public HttpClient.Version version() {
      return responseInfo.version();
    }
  }
  //</editor-fold>
}
