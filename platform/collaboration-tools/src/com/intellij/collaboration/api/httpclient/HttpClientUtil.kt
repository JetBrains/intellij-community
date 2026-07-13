// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_GZIP
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_HEADER
import com.intellij.collaboration.api.logName
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.StringReader
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandler
import java.net.http.HttpResponse.BodySubscriber
import java.net.http.HttpResponse.BodySubscribers
import java.net.http.HttpResponse.ResponseInfo
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import java.util.zip.GZIPInputStream

object HttpClientUtil {

  const val ACCEPT_ENCODING_HEADER = "Accept-Encoding"
  const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP = "gzip"

  const val ACCEPT_HEADER: String = "Accept"
  const val CONTENT_TYPE_HEADER = "Content-Type"
  const val CONTENT_TYPE_JSON = "application/json"
  const val CONTENT_TYPE_ENCODED_FORM: String = "application/x-www-form-urlencoded"

  const val USER_AGENT_HEADER = "User-Agent"

  /**
   * Checks the status code of the response and throws [HttpStatusErrorException] if status code is not a successful one
   *
   * Logs request status code and also response body if tracing is enabled in logger
   */
  fun checkStatusCodeWithLogging(logger: Logger, requestName: String, statusCode: Int, bodyStream: InputStream) {
    logger.debug("$requestName : Status code $statusCode")
    if (statusCode >= 400) {
      val errorBody = bodyStream.reader().use { it.readText() }
      if (logger.isTraceEnabled) {
        logger.trace("$requestName : Response body: $errorBody")
      }
      throw HttpStatusErrorException(requestName, statusCode, errorBody)
    }
  }

  /**
   * Reads the response from input stream, logging the response if tracing is enabled in logger
   *
   * It is usually better to read the response directly from stream to avoid creating too many strings,
   * but when tracing is enabled we need to read the response to string first to log it
   */
  private fun responseReaderWithLogging(logger: Logger, requestName: String, stream: InputStream): Reader {
    if (logger.isTraceEnabled) {
      val body = stream.reader().use { it.readText() }
      logger.trace("$requestName : Response body: $body")
      return StringReader(body)
    }
    return stream.reader()
  }

  /**
   * Shorthand for creating a body handler that inflates the incoming response body if it is zipped, checks that
   * the status code is OK (throws [HttpStatusErrorException] otherwise), and applies the given function to read
   * the result body and map it to some value.
   *
   * @param logger The logger to log non-OK status codes in.
   * @param request The request performed, for logging purposes.
   * @param mapToResult Maps a response to a result value. Exceptions thrown from this function are not logged by
   * [inflateAndReadWithErrorHandlingAndLogging].
   */
  fun <T> inflateAndReadWithErrorHandlingAndLogging(
    logger: Logger,
    request: HttpRequest,
    mapToResult: (Reader, ResponseInfo) -> T,
  ): BodyHandler<T> = InflatedStreamReadingBodyHandler { responseInfo, bodyStream ->
    checkStatusCodeWithLogging(logger, request.logName(), responseInfo.statusCode(), bodyStream)
    responseReaderWithLogging(logger, request.logName(), bodyStream).use { reader: Reader ->
      mapToResult(reader, responseInfo)
    }
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


class ByteArrayProducingBodyPublisher(
  private val producer: () -> ByteArray,
) : HttpRequest.BodyPublisher {

  override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
    HttpRequest.BodyPublishers.ofByteArray(producer()).subscribe(subscriber)
  }

  override fun contentLength(): Long = -1
}

/**
 * Body handler that inflates the incoming response body if it is zipped, and applies the given function to read
 * the result body and map it to some value.
 *
 * Stream passed to [streamReader] will not react to [InputStream.close] calls, to avoid cancelling the request and spawning an obscure
 * "chunked transfer encoding, state: READING_LENGTH" error.
 */
class InflatedStreamReadingBodyHandler<T>(
  private val streamReader: (responseInfo: ResponseInfo, bodyStream: InputStream) -> T,
) : BodyHandler<T> {

  override fun apply(responseInfo: ResponseInfo): BodySubscriber<T> {
    val inputStreamSubscriber = BodySubscribers.ofInputStream()

    val isGzipContent = responseInfo.headers()
      .allValues(CONTENT_ENCODING_HEADER)
      .contains(CONTENT_ENCODING_GZIP)

    val subscriber = if (isGzipContent) {
      BodySubscribers.mapping<InputStream, InputStream>(inputStreamSubscriber, ::GZIPInputStream)
    }
    else {
      inputStreamSubscriber
    }

    return BodySubscribers.mapping(subscriber) {
      val originalStream = it ?: return@mapping null
      streamReader(responseInfo, UnclosableInputStream(originalStream))
    }
  }
}

private class UnclosableInputStream(private val original: InputStream) : InputStream() {
  override fun close() {
    // do nothing
  }

  override fun read(): Int = original.read()
  override fun read(b: ByteArray?): Int = original.read(b)
  override fun read(b: ByteArray?, off: Int, len: Int): Int = original.read(b, off, len)
  override fun readAllBytes(): ByteArray? = original.readAllBytes()
  override fun readNBytes(len: Int): ByteArray? = original.readNBytes(len)
  override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int = original.readNBytes(b, off, len)
  override fun skip(n: Long): Long = original.skip(n)
  override fun skipNBytes(n: Long) = original.skipNBytes(n)
  override fun available(): Int = original.available()
  override fun mark(readlimit: Int) = original.mark(readlimit)
  override fun reset() = original.reset()
  override fun markSupported(): Boolean = original.markSupported()
  override fun transferTo(out: OutputStream?): Long = original.transferTo(out)
  override fun equals(other: Any?): Boolean = original.equals(other)
  override fun hashCode(): Int = original.hashCode()
  override fun toString(): String = original.toString()
}
