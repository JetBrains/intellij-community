// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_GZIP
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_HEADER
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflateAndReadWithErrorHandlingAndLogging
import com.intellij.collaboration.api.logName
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.future.await
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.net.http.HttpRequest
import java.net.http.HttpResponse.*
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import java.util.zip.GZIPInputStream

object HttpClientUtil {

  const val ACCEPT_ENCODING_HEADER = "Accept-Encoding"
  const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP = "gzip"

  const val CONTENT_TYPE_HEADER = "Content-Type"
  const val CONTENT_TYPE_JSON = "application/json"

  const val USER_AGENT_HEADER = "User-Agent"

  /**
   * Checks the status code of the response and throws [HttpStatusErrorException] if status code is not a successful one
   *
   * Logs request status code and also response body if tracing is enabled in logger
   */
  fun checkStatusCodeWithLogging(logger: Logger, requestName: String, statusCode: Int, bodyStream: InputStream) {
    logger.debug("$requestName : Status code $statusCode")
    if (statusCode >= 400) {
      val errorBody = bodyStream.reader().readText()
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
   * Reads the request response if the request completed successfully, otherwise throws [HttpStatusErrorException]
   * Response status is always logged, response body is logged when tracing is enabled in logger
   */
  fun <T> readSuccessResponseWithLogging(
    logger: Logger,
    request: HttpRequest,
    responseInfo: ResponseInfo,
    bodyStream: InputStream,
    reader: (Reader) -> T,
  ): T {
    checkStatusCodeWithLogging(logger, request.logName(), responseInfo.statusCode(), bodyStream)
    return responseReaderWithLogging(logger, request.logName(), bodyStream).use(reader)
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
    readSuccessResponseWithLogging(logger, request, responseInfo, bodyStream) { reader ->
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

@Deprecated("Should be replaced by a Ktor HTTP-client by 24.3")
class LazyBodyHandler<T>(
  private val delegate: BodyHandler<T>,
) : BodyHandler<suspend () -> T> {
  override fun apply(responseInfo: ResponseInfo?): BodySubscriber<(suspend () -> T)?>? {
    val delegateSubscriber = delegate.apply(responseInfo)

    return object : BodySubscriber<(suspend () -> T)?> by delegateSubscriber {
      override fun getBody(): CompletionStage<(suspend () -> T)?>? {
        return CompletableFuture.completedFuture {
          delegateSubscriber.body.await()
        }
      }
    }
  }
}

class InflatedStreamReadingBodyHandler<T>(
  private val streamReader: (responseInfo: ResponseInfo, bodyStream: InputStream) -> T,
) : BodyHandler<T> {

  override fun apply(responseInfo: ResponseInfo): BodySubscriber<T> {
    val inputStreamSubscriber = BodySubscribers.ofInputStream()

    val isGzipContent = responseInfo.headers()
      .allValues(CONTENT_ENCODING_HEADER)
      .contains(CONTENT_ENCODING_GZIP)

    val subscriber = if (isGzipContent) {
      BodySubscribers.mapping<InputStream?, InputStream?>(inputStreamSubscriber, ::GZIPInputStream)
    }
    else {
      inputStreamSubscriber
    }

    return BodySubscribers.mapping(subscriber) {
      streamReader(responseInfo, it)
    }
  }
}
