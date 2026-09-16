package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.Credentials
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.HttpStatusException
import org.jetbrains.intellij.build.io.OwnedHttpClient
import org.jetbrains.intellij.build.io.runHttpTask
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.Charset
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Owns a connection pool. Close the session after all callers have joined their request tasks. */
@ApiStatus.Internal
class BuildHttpSession(
  val requestTimeout: Duration = 2.hours,
  internal val initialRetryDelay: Duration = 5.seconds,
) : AutoCloseable {
  private var owner: OwnedHttpClient? = null
  private var closed = false

  init {
    require(requestTimeout.isFinite() && requestTimeout > Duration.ZERO) { "The request timeout must be positive and finite" }
    require(initialRetryDelay.isFinite() && initialRetryDelay >= Duration.ZERO) { "The retry delay must be finite and non-negative" }
  }

  @Synchronized
  internal fun checkOpen() {
    check(!closed) { "The HTTP session is closed" }
  }

  @Synchronized
  internal fun client(): HttpClient {
    checkOpen()
    return (owner ?: OwnedHttpClient(requestTimeout, HttpClient.Version.HTTP_2, HttpClient.Redirect.NEVER).also { owner = it }).client
  }

  @Synchronized
  override fun close() {
    if (closed) return
    closed = true
    try {
      owner?.close()
    }
    finally {
      owner = null
    }
  }

  /** Returns a consumed response. Only GET and HEAD requests can have more than one attempt. */
  fun send(request: HttpRequest, authentication: BuildHttpAuthentication? = null, attempts: Int = 1): BuildHttpResponse {
    return consume(request, authentication, attempts) { response ->
      BuildHttpResponse(response.statusCode(), response.headers(), response.uri(), if (request.method() == "HEAD") "" else readHttpText(response))
    }
  }

  /** Closes the response body before returning. The consumer must not retain the body stream. */
  fun <T> consume(
    request: HttpRequest,
    authentication: BuildHttpAuthentication? = null,
    attempts: Int = 1,
    retryBodyFailures: Boolean = true,
    action: (HttpResponse<InputStream>) -> T,
  ): T {
    require(attempts > 0) { "The request must have at least one attempt" }
    require(attempts == 1 || request.method() == "GET" || request.method() == "HEAD") { "Only GET and HEAD requests can be retried" }
    return runHttpTask {
      val failures = ArrayList<Exception>()
      for (attempt in 1..attempts) {
        checkHttpInterrupted()
        val state = HttpAttempt()
        try {
          return@runHttpTask runHttpTask(requestTimeout) {
            exchange(request, authentication) { response ->
              state.retryAfter = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()
                                   ?.coerceIn(0, Long.MAX_VALUE / 1000)?.times(1000) ?: 0
              if (attempt < attempts && response.statusCode() in 500..599) throw httpStatusException(response)
              state.consuming = true
              action(response)
            }
          }
        }
        catch (failure: Exception) {
          checkHttpInterrupted()
          if (attempt == attempts || state.consuming && !retryBodyFailures || !isHttpRetryAllowed(failure)) {
            failures.filter { it !== failure }.forEach(failure::addSuppressed)
            throw failure
          }
          failures.add(failure)
          val delay = if (initialRetryDelay == Duration.ZERO) 0
          else
            max(state.retryAfter, (1000L shl (attempt - 1).coerceAtMost(6)).coerceAtMost(60_000) + Random.nextLong(1001))
          Thread.sleep(delay)
        }
      }
      error("The HTTP attempts did not produce a result")
    }
  }

  private fun <T> exchange(
    original: HttpRequest,
    authentication: BuildHttpAuthentication?,
    action: (HttpResponse<InputStream>) -> T,
  ): T {
    val client = client()
    var uri = original.uri()
    var authorization = when (authentication) {
      is BuildHttpAuthentication.Bearer -> "Bearer ${authentication.token}"
      else -> original.headers().firstValue("Authorization").orElse(null)
    }
    var challenged = false
    var credentialsAllowed = true
    repeat(20) {
      checkHttpInterrupted()
      val builder = HttpRequest.newBuilder(original) { name, _ -> !name.equals("Authorization", ignoreCase = true) }.uri(uri)
      if (original.headers().firstValue("User-Agent").isEmpty) builder.header("User-Agent", "Build Script Downloader")
      if (original.headers().firstValue("Accept-Encoding").isEmpty) builder.header("Accept-Encoding", "deflate;q=1.0,gzip;q=0.9")
      authorization?.let { builder.setHeader("Authorization", it) }
      OwnedHttpResponseBody().use { body ->
        val response = client.send(builder.build()) { body.subscriber }
        if (response.statusCode() == 401 && !challenged && credentialsAllowed && authentication is BuildHttpAuthentication.Basic &&
            response.headers().allValues("WWW-Authenticate").any { header -> BASIC_CHALLENGE.containsMatchIn(header) }) {
          challenged = true
          val credentials = authentication.credentialsProvider()
          authorization = "Basic " + Base64.getEncoder().encodeToString("${credentials.username}:${credentials.password}".toByteArray())
          return@repeat
        }
        if (original.method() == "GET" || original.method() == "HEAD") {
          val location = response.headers().firstValue("Location").orElse(null)
          if (response.statusCode() in REDIRECT_STATUSES && location != null) {
            val target = uri.resolve(location)
            if ((!target.scheme.equals("http", ignoreCase = true) && !target.scheme.equals("https", ignoreCase = true)) ||
                uri.scheme.equals("https", ignoreCase = true) && target.scheme.equals("http", ignoreCase = true)) {
              throw httpStatusException(response)
            }
            if (!sameOrigin(uri, target)) {
              authorization = null
              credentialsAllowed = false
            }
            uri = target
            return@repeat
          }
        }
        return action(response)
      }
    }
    throw IOException("The HTTP request exceeded 20 send exchanges: ${original.uri()}")
  }
}

private class HttpAttempt {
  var consuming = false
  var retryAfter = 0L
}

/** Owns the response stream even when cancellation prevents the caller from receiving the response. */
internal class OwnedHttpResponseBody : AutoCloseable {
  private var body: InputStream? = null
  private var closed = false
  val subscriber: HttpResponse.BodySubscriber<InputStream> = HttpResponse.BodySubscribers.mapping(
    HttpResponse.BodySubscribers.ofInputStream(), ::register
  )

  @Synchronized
  private fun register(stream: InputStream): InputStream {
    if (closed) stream.close()
    else body = stream
    return stream
  }

  @Synchronized
  override fun close() {
    closed = true
    val stream = body
    body = null
    stream?.close()
  }
}

/** Keeps credentials local to the request that supplies them. */
@ApiStatus.Internal
sealed class BuildHttpAuthentication {
  class Bearer(internal val token: String) : BuildHttpAuthentication()
  class Basic(internal val credentialsProvider: () -> Credentials) : BuildHttpAuthentication()
}

/** Contains no open streams or connection resources. */
@ApiStatus.Internal
data class BuildHttpResponse(
  @JvmField val statusCode: Int,
  @JvmField val headers: HttpHeaders,
  @JvmField val uri: URI,
  @JvmField val body: String,
) {
  fun requireSuccess(): BuildHttpResponse {
    if (statusCode !in 200..299) throw HttpStatusException(httpErrorMessage(headers, body.take(1024)), statusCode, uri.toString())
    return this
  }
}

/** Borrows [session], or closes a temporary session after the action and its request tasks finish. */
@ApiStatus.Internal
fun <T> withBuildHttpSession(session: BuildHttpSession? = null, action: (BuildHttpSession) -> T): T {
  return runHttpTask {
    if (session == null) BuildHttpSession().use(action) else action(session)
  }
}

internal fun checkHttpInterrupted() {
  if (Thread.currentThread().isInterrupted) throw InterruptedException("The HTTP request was interrupted")
}

internal fun isHttpRetryAllowed(failure: Exception): Boolean {
  if (generateSequence<Throwable>(failure) { it.cause }.any {
      it is InterruptedException || it is CancellationException ||
      it is TimeoutException || it is HttpTimeoutException
    }) return false
  return failure is IOException || failure is HttpStatusException && failure.statusCode in 500..599
}

internal fun httpStatusException(response: HttpResponse<InputStream>): HttpStatusException {
  val body = decodedHttpBody(response).use { it.readNBytes(1024).toString(Charsets.UTF_8) }
  return HttpStatusException(httpErrorMessage(response.headers(), body), response.statusCode(), response.uri().toString())
}

private fun httpErrorMessage(headers: HttpHeaders, body: String): String {
  return buildString {
    append("Cannot download\n")
    for ((name, values) in headers.map().toSortedMap()) {
      for (value in values) append("Header: $name: $value\n")
    }
    append('\n').append(body)
  }
}

internal fun decodedHttpBody(response: HttpResponse<InputStream>): InputStream {
  var body = response.body()
  val encodings = response.headers().allValues("Content-Encoding").flatMap { it.split(',') }.asReversed()
  try {
    for (encoding in encodings) {
      body = when (encoding.trim().lowercase()) {
        "identity", "" -> body
        "gzip" -> GZIPInputStream(body)
        "deflate" -> RawDeflateInputStream(body)
        else -> throw IOException("Unsupported Content-Encoding: $encoding")
      }
    }
  }
  catch (failure: Throwable) {
    body.use { throw failure }
  }
  return body
}

private class RawDeflateInputStream(input: InputStream) : InflaterInputStream(input, Inflater(true)) {
  override fun close() {
    try {
      super.close()
    }
    finally {
      inf.end()
    }
  }
}

private fun readHttpText(response: HttpResponse<InputStream>): String {
  val contentType = response.headers().firstValue("Content-Type").orElse(null) ?: ""
  val charsetName = CHARSET.find(contentType)?.groupValues?.get(1)?.trim('"', '\'')
  val charset = if (charsetName == null) Charsets.UTF_8 else Charset.forName(charsetName)
  return decodedHttpBody(response).use {
    val bytes = if (response.statusCode() in 200..299) it.readAllBytes() else it.readNBytes(1024)
    bytes.toString(charset)
  }
}

private fun sameOrigin(first: URI, second: URI): Boolean {
  fun port(uri: URI): Int = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
  return first.scheme.equals(second.scheme, ignoreCase = true) && first.host.equals(second.host, ignoreCase = true) && port(first) == port(second)
}

private val BASIC_CHALLENGE = Regex("(?:^|,)\\s*Basic\\b", RegexOption.IGNORE_CASE)
private val CHARSET = Regex("(?:^|;)\\s*charset\\s*=\\s*([^;\\s]+)", RegexOption.IGNORE_CASE)
private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)
