// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.HttpClientUtil.ACCEPT_ENCODING_HEADER
import com.intellij.collaboration.api.httpclient.HttpClientUtil.ACCEPT_HEADER
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_GZIP
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_HEADER
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_TYPE_HEADER
import com.intellij.collaboration.api.httpclient.HttpClientUtil.MIME_TYPE_APPLICATION_PREFIX
import com.intellij.collaboration.api.httpclient.HttpClientUtil.MIME_TYPE_JSON_SUFFIX
import com.intellij.collaboration.api.httpclient.HttpClientUtil.MIME_TYPE_TEXT_PREFIX
import com.intellij.collaboration.api.httpclient.HttpClientUtil.MIME_TYPE_XML_SUFFIX
import com.intellij.microservices.mime.MimeTypes
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.apache.http.entity.ContentType
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.util.Optional
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLSession
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.optionals.getOrNull

private val LOG = Logger.getInstance(HttpClientUtil::class.java)

object HttpClientUtil {

  const val ACCEPT_ENCODING_HEADER: String = "Accept-Encoding"
  const val CONTENT_ENCODING_HEADER: String = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP: String = "gzip"

  const val ACCEPT_HEADER: String = "Accept"
  const val CONTENT_TYPE_HEADER: String = "Content-Type"

  const val MIME_TYPE_TEXT_PREFIX: String = "text/"
  const val MIME_TYPE_APPLICATION_PREFIX: String = "application/"
  const val MIME_TYPE_JSON_SUFFIX: String = "+json"
  const val MIME_TYPE_XML_SUFFIX: String = "+xml"

  const val MIME_TYPE_JSON: String = MimeTypes.APPLICATION_JSON
  const val MIME_TYPE_ENCODED_FORM: String = MimeTypes.APPLICATION_FORM_URLENCODED

  const val USER_AGENT_HEADER: String = "User-Agent"

  const val ETAG_HEADER: String = "ETag"
  const val IF_NONE_MATCH_HEADER: String = "If-None-Match"

  fun getRequestName(httpMethod: String, uri: URI): String = "Request $httpMethod $uri}"
  fun getRequestName(request: HttpRequest): String = getRequestName(request.method(), request.uri())

  fun isJsonMimeType(mimeType: String): Boolean {
    val lc = mimeType.lowercase()
    return lc == MimeTypes.APPLICATION_JSON
           || lc.startsWith(MIME_TYPE_APPLICATION_PREFIX) && lc.endsWith(MIME_TYPE_JSON_SUFFIX)
  }

  /**
   * Build the User-Agent header value for the [agentName]
   * Append product, java and OS data
   */
  fun getUserAgentValue(agentName: String): String {
    val ideName = ApplicationNamesInfo.getInstance().fullProductName.replace(' ', '-')
    val ideBuild =
      if (ApplicationManager.getApplication().isUnitTestMode) "test"
      else ApplicationInfo.getInstance().build.asStringWithoutProductCode()
    val java = "JRE " + SystemInfo.JAVA_RUNTIME_VERSION
    val os = SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION
    val arch = SystemInfo.OS_ARCH

    return "$agentName $ideName/$ideBuild ($java; $os; $arch)"
  }
}

/**
 * Override the `Accept-Encoding` header to be `gzip`
 */
@ApiStatus.Experimental
fun HttpRequest.forceAcceptGzipEncoding(): HttpRequest =
  HttpRequest.newBuilder(this) { k, v ->
    // only allow gzip encoding
    !k.equals(ACCEPT_ENCODING_HEADER, true) || v.equals(CONTENT_ENCODING_GZIP, true)
  }.header(ACCEPT_ENCODING_HEADER, CONTENT_ENCODING_GZIP).build()

/**
 * Set the `Accept` header to be [mimeType] if the header is missing
 */
@ApiStatus.Experimental
fun HttpRequest.ensureAcceptHeader(mimeType: String): HttpRequest {
  if (headers().firstValue(ACCEPT_HEADER).isPresent) return this
  return HttpRequest.newBuilder(this) { _, _ ->
    true
  }.header(ACCEPT_HEADER, mimeType).build()
}

/**
 * A blocking delegate of [checkStatusCodeWithLogging].
 *
 * NB: Can be interrupted. [HttpStatusErrorException.body] will be `null` in case of interrupt
 *
 * @see checkStatusCodeWithLogging
 */
@ApiStatus.Experimental
@RequiresBlockingContext(replaceWith = ReplaceWith("checkStatusCodeWithLogging"))
@Throws(HttpStatusErrorException::class)
fun HttpResponse<out InputStream>.checkStatusCodeWithLoggingBlocking(logger: Logger, requestName: String) {
  val statusCode = statusCode()
  logger.debug("$requestName : Status code $statusCode")
  if (statusCode >= 400) {
    val errorBody = try {
      readBodyAsText()
    }
    catch (e: IOException) {
      logger.debug("$requestName : Could not read the error response body", e)
      null
    }
    if (errorBody != null && logger.isTraceEnabled) {
      logger.trace("$requestName : Response body: $errorBody")
    }
    throw HttpStatusErrorException(requestName, statusCode, errorBody)
  }
}

/**
 * Checks the status code of the response and throws [HttpStatusErrorException] if status code is not a successful one (>=400)
 * Reads the textual (depending on the `Content-Type`) response body into [HttpStatusErrorException.body]
 *
 * Logs request status code and also response body if tracing is enabled in logger and the response body is textual
 * according to content type header
 */
@ApiStatus.Experimental
@Throws(HttpStatusErrorException::class)
suspend fun HttpResponse<out InputStream>.checkStatusCodeWithLogging(logger: Logger, requestName: String) {
  runInterruptibleHttpBodyRead(Dispatchers.IO) {
    checkStatusCodeWithLoggingBlocking(logger, requestName)
  }
}

/**
 * A blocking version of [readBodyWithLogging].
 *
 * NB: Can be interrupted, but will throw an [IOException] instead of [InterruptedException]
 *
 * @see readBodyWithLogging
 */
@ApiStatus.Experimental
@RequiresBlockingContext(replaceWith = ReplaceWith("readBodyWithLogging"))
@Throws(IOException::class)
fun <T> HttpResponse<out InputStream>.readBodyWithLoggingBlocking(
  logger: Logger,
  requestName: String,
  reader: InputStream.(mimeType: String?, charset: Charset?) -> T,
): T {
  logger.debug("$requestName : Reading response")
  val contentType = getContentType()
  return readBodyInflating {
    if (logger.isTraceEnabled) {
      if (contentType != null && isMimeTypeTextual(contentType.first)) {
        val out = ByteArrayOutputStream().also {
          transferTo(it)
        }.toByteArray()

        val body = String(out, contentType.second ?: Charsets.UTF_8)
        logger.trace("$requestName : Response body: $body")

        ByteArrayInputStream(out).use {
          reader(contentType.first, contentType.second)
        }
      }
      else {
        logger.trace("$requestName : Non-textual content")
        reader(contentType?.first, contentType?.second)
      }
    }
    else {
      reader(contentType?.first, contentType?.second)
    }
  }
}

/**
 * Reads the response body with [reader] taking care of response gzip encoding
 *
 * If the logging level for [logger] is set to `TRACE`, logs the textual response body
 */
@ApiStatus.Experimental
@Throws(IOException::class)
suspend fun <T> HttpResponse<out InputStream>.readBodyWithLogging(
  logger: Logger,
  requestName: String,
  reader: InputStream.(mimeType: String?, charset: Charset?) -> T,
): T {
  return runInterruptibleHttpBodyRead(Dispatchers.IO) {
    readBodyWithLoggingBlocking(logger, requestName, reader)
  }
}

private fun isMimeTypeTextual(mimeType: String): Boolean {
  val lc = mimeType.lowercase()
  return lc.startsWith(MIME_TYPE_TEXT_PREFIX) && !lc.contains("stream")
         || lc in MimeTypes.PREDEFINED_TEXT_MIME_TYPES
         || lc.startsWith(MIME_TYPE_APPLICATION_PREFIX) && lc.endsWith(MIME_TYPE_JSON_SUFFIX)
         || lc.startsWith(MIME_TYPE_APPLICATION_PREFIX) && lc.endsWith(MIME_TYPE_XML_SUFFIX)
}

@Throws(IOException::class)
private fun HttpResponse<out InputStream>.readBodyAsText(): String? {
  val (mimeType, charset) = getContentType() ?: return null

  if (!isMimeTypeTextual(mimeType)) {
    return null
  }
  return readBodyInflating {
    bufferedReader(charset ?: Charsets.UTF_8).use {
      it.readText()
    }
  }
}

private fun HttpResponse<*>.getContentType(): Pair<String, Charset?>? {
  val value = headers().firstValue(CONTENT_TYPE_HEADER)?.getOrNull() ?: return null
  val contentType = try {
    ContentType.parse(value)
  }
  catch (e: Exception) {
    LOG.debug("Could not parse $CONTENT_TYPE_HEADER from value $value", e)
    return null
  }
  val mimeType = contentType.mimeType ?: return null
  val charset = contentType.charset
  return mimeType to charset
}

/**
 * Reads the response body with a [streamReader] taking care of the gzip encoding
 */
private fun <T> HttpResponse<out InputStream>.readBodyInflating(streamReader: InputStream.() -> T): T {
  val encoding = headers().firstValue(CONTENT_ENCODING_HEADER)
  val isGzipEncoding = encoding.map {
    check(it.equals(CONTENT_ENCODING_GZIP, true)) { "Unsupported encoding: $it" }
    true
  }.orElseGet {
    false
  }

  val bodyStream = if (isGzipEncoding) {
    GZIPInputStream(body())
  }
  else {
    body()
  }

  return bodyStream.use {
    it.streamReader()
  }
}

private suspend fun <T> runInterruptibleHttpBodyRead(
  context: CoroutineContext = EmptyCoroutineContext,
  block: () -> T,
): T {
  return try {
    runInterruptible(context, block)
  }
  catch (e: IOException) {
    // An interrupted blocking read does not surface as a raw InterruptedException (which runInterruptible would
    // translate on its own): the stream reports it as an IOException. Normalize any such cancellation-induced
    // failure to a CancellationException; genuine failures (job still active) are rethrown as-is.
    currentCoroutineContext().ensureActive()
    throw e
  }
}

/**
 * Overrides the response body with [newBody]
 */
@ApiStatus.Experimental
fun <T> HttpResponse<*>.withNewBody(newBody: T): HttpResponse<T> = BodyOverrideHttpResponse(this, newBody)

private class BodyOverrideHttpResponse<T>(
  private val original: HttpResponse<*>,
  private val body: T,
) : HttpResponse<T> {
  override fun request(): HttpRequest? = original.request()
  override fun uri(): URI? = original.uri()
  override fun version(): HttpClient.Version? = original.version()
  override fun statusCode(): Int = original.statusCode()
  override fun headers(): HttpHeaders? = original.headers()
  override fun sslSession(): Optional<SSLSession?>? = original.sslSession()

  /**
   * body() of previousResponse is always null according to [HttpResponse.body] docs
   */
  override fun previousResponse(): Optional<HttpResponse<T?>?>? = original.previousResponse().map { it.withNewBody(null) }
  override fun body(): T = body
}