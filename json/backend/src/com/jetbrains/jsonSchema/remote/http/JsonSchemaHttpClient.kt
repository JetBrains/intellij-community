// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.net.JdkProxyProvider
import com.intellij.util.net.ssl.CertificateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException
import java.time.Duration
import javax.net.ssl.SSLContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

private val REQUEST_TIMEOUT: Duration get() = Duration.ofMillis(Registry.intValue("json.schema.remote.http.timeout", 10_000).toLong())
private val ATTEMPTS: Int get() = Registry.intValue("json.schema.remote.http.max.retries", 5)
private val RETRY_DELAY_MILLIS: Long get() = Registry.intValue("json.schema.remote.http.backoff.step", 1_000).toLong()
private val MAX_SCHEMA_BYTES: Int get() = Registry.intValue("json.schema.remote.http.max.schema.bytes", 16 * 1024 * 1024).coerceAtLeast(1)
private val MAX_REDIRECTS: Int get() = Registry.intValue("json.schema.remote.http.max.redirects", 5).coerceAtLeast(0)

private const val READ_BUFFER_SIZE = 64 * 1024

/**
 * Downloads remote schemas with the JDK [HttpClient].
 * The client uses the IDE proxy settings, the proxy credentials, and the IDE trust store.
 */
@org.jetbrains.annotations.ApiStatus.Internal
class JsonSchemaHttpClient : SchemaHttpTransport, Closeable, Disposable {
  private val timeout: Duration = REQUEST_TIMEOUT
  private val client: HttpClient = createClient(timeout)

  override suspend fun download(url: SchemaUrl): DownloadResult =
    download(url, null, null)

  override suspend fun refresh(
    url: SchemaUrl,
    etag: String?,
    lastModified: String?,
  ): DownloadResult = download(url, etag, lastModified)

  private suspend fun download(
    url: SchemaUrl,
    etag: String?,
    lastModified: String?,
  ): DownloadResult {
    var lastFailure: Throwable? = null
    val attemptCount = ATTEMPTS.coerceAtLeast(1)
    repeat(attemptCount) { attempt ->
      try {
        val result = downloadOnce(url, etag, lastModified)
        if (result !is DownloadResult.Failed || attempt == attemptCount - 1) return result
        lastFailure = result.cause
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        if (!isNetworkFailure(e)) throw e
        lastFailure = e
        if (attempt == attemptCount - 1) return DownloadResult.Failed(e)
      }
      delay((attempt * RETRY_DELAY_MILLIS).milliseconds)
    }
    return DownloadResult.Failed(lastFailure ?: IOException("Remote schema download failed"))
  }

  private suspend fun downloadOnce(
    url: SchemaUrl,
    etag: String?,
    lastModified: String?,
    redirects: Int = 0,
  ): DownloadResult {
    if (!isValidRemoteUrl(url)) return DownloadResult.Rejected("Only HTTP(S) URLs without user information are allowed")
    val request = HttpRequest.newBuilder(URI(url.value)).timeout(timeout).GET().apply {
      if (redirects == 0) {
        etag?.let { header("If-None-Match", it) }
        lastModified?.let { header("If-Modified-Since", it) }
      }
    }.build()
    val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()
    return response.body().use { body ->
      val status = response.statusCode()
      when {
        status == 304 -> DownloadResult.NotModified
        status == 200 -> handleSuccess(response, body, url)
        status == 404 -> DownloadResult.Error(status, statusMessage(status))
        status in 300..399 -> handleRedirect(response, url, redirects)
        status >= 500 -> DownloadResult.Failed(IOException("Remote schema server returned HTTP $status"))
        status in 201..299 -> DownloadResult.Failed(IOException("Remote schema server returned unexpected HTTP $status"))
        else -> DownloadResult.Error(status, statusMessage(status))
      }
    }
  }

  private suspend fun handleSuccess(response: HttpResponse<InputStream>, body: InputStream, url: SchemaUrl): DownloadResult {
    val maxSchemaBytes = MAX_SCHEMA_BYTES
    val declaredLength = response.headers().firstValueAsLong("Content-Length")
    if (declaredLength.isPresent && declaredLength.asLong > maxSchemaBytes) {
      return DownloadResult.Error.OVERSIZED_CONTENT
    }
    val bytes = readBody(body, maxSchemaBytes) ?: return DownloadResult.Error.OVERSIZED_CONTENT
    return DownloadResult.Success(
      SchemaContent(bytes),
      response.headers().firstValue("ETag").orElse(null),
      response.headers().firstValue("Last-Modified").orElse(null),
      url.value,
    )
  }

  /**
   * Returns `null` when the body exceeds [maxBytes].
   * The JDK client has no read timeout, so each read waits at most [timeout].
   */
  private suspend fun readBody(body: InputStream, maxBytes: Int): ByteArray? {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(READ_BUFFER_SIZE)
    while (true) {
      val read = withTimeoutOrNull(timeout.toKotlinDuration()) {
        runInterruptible(Dispatchers.IO) { body.read(buffer) }
      } ?: throw HttpTimeoutException("Remote schema read timed out")
      if (read < 0) return result.toByteArray()
      if (result.size() + read > maxBytes) return null
      result.write(buffer, 0, read)
    }
  }

  // Followed manually (the JDK redirect policy is NEVER, see createClient()) so each redirect
  // target is checked for an HTTP(S) scheme, a host, and absent user info before it is requested.
  private suspend fun handleRedirect(
    response: HttpResponse<InputStream>,
    current: SchemaUrl,
    redirects: Int,
  ): DownloadResult {
    if (redirects >= MAX_REDIRECTS) {
      return DownloadResult.Error.REDIRECT_LIMIT_EXCEEDED
    }
    val location = response.headers().firstValue("Location").orElse(null)
                   ?: return DownloadResult.Failed(IOException("Redirect response has no Location header"))
    val redirected = try {
      SchemaUrl.parse(URI(current.value).resolve(location).toString())
    }
    catch (e: Exception) {
      logger<JsonSchemaHttpClient>().info("Unable to parse redirect url $location", e)
      return DownloadResult.Error.INVALID_REDIRECT_TARGET
    }
    if (!isValidRemoteUrl(redirected)) {
      // Error instead of Rejected because URL is valid, results it not
      return DownloadResult.Error.FORBIDDEN_REDIRECT_TARGET
    }
    // etag/lastModified are dropped because conditional headers are only ever valid for the original (non-redirected) request.
    return downloadOnce(redirected, null, null, redirects + 1)
  }

  override fun close() {
    client.shutdownNow()
  }

  override fun dispose() {
    close()
  }
}

private fun createClient(timeout: Duration): HttpClient {
  val proxyProvider = JdkProxyProvider.getInstance()
  return HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    // Redirects are followed manually in downloadOnce()/handleRedirect() so each redirect target
    // is checked for an HTTP(S) scheme, a host, and absent user info.
    .followRedirects(HttpClient.Redirect.NEVER)
    .proxy(proxyProvider.proxySelector)
    .authenticator(proxyProvider.authenticator)
    // Some products, for example the language server, do not register the CertificateManager service.
    .sslContext(serviceOrNull<CertificateManager>()?.sslContext ?: SSLContext.getDefault())
    .connectTimeout(timeout)
    .executor(ProcessIOExecutorService.INSTANCE)
    .build()
}

/** The JDK client does not expose the reason phrase of a response. */
private fun statusMessage(status: Int): String = "HTTP $status"

// An unknown host can surface as UnresolvedAddressException, which is not an IOException.
private fun isNetworkFailure(e: Exception): Boolean = e is IOException || e is UnresolvedAddressException

private fun isValidRemoteUrl(url: SchemaUrl): Boolean {
  val uri = try {
    URI(url.value)
  }
  catch (_: Exception) {
    return false
  }
  return uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null
}
