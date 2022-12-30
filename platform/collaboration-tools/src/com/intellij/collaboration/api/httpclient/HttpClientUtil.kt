// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_GZIP
import com.intellij.collaboration.api.httpclient.HttpClientUtil.CONTENT_ENCODING_HEADER
import com.intellij.collaboration.api.logName
import com.intellij.openapi.diagnostic.Logger
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.ResponseInfo
import java.nio.ByteBuffer
import java.util.concurrent.Flow
import java.util.zip.GZIPInputStream

object HttpClientUtil {

  const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP = "gzip"

  const val CONTENT_TYPE_HEADER = "Content-Type"
  const val CONTENT_TYPE_JSON = "application/json"

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
  fun responseReaderWithLogging(logger: Logger, requestName: String, stream: InputStream): Reader {
    if (logger.isTraceEnabled) {
      val body = stream.reader().readText()
      logger.trace("$requestName : Response body: $body")
      return StringReader(body)
    }
    return stream.reader()
  }

  /**
   * Reads the request response if the request completed successfully, otherwise throws [HttpStatusErrorException]
   * Response status is always logged, response body is logged when tracing is enabled in logger
   */
  fun <T> readSuccessResponseWithLogging(logger: Logger,
                                         request: HttpRequest,
                                         responseInfo: ResponseInfo,
                                         bodyStream: InputStream,
                                         reader: (Reader) -> T): T {
    checkStatusCodeWithLogging(logger, request.logName(), responseInfo.statusCode(), bodyStream)
    return responseReaderWithLogging(logger, request.logName(), bodyStream).use(reader)
  }
}


class ByteArrayProducingBodyPublisher(
  private val producer: () -> ByteArray
) : HttpRequest.BodyPublisher {

  override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
    HttpRequest.BodyPublishers.ofByteArray(producer()).subscribe(subscriber)
  }

  override fun contentLength(): Long = -1
}


class InflatedStreamReadingBodyHandler<T>(
  private val streamReader: (responseInfo: ResponseInfo, bodyStream: InputStream) -> T
) : HttpResponse.BodyHandler<T> {

  override fun apply(responseInfo: ResponseInfo): HttpResponse.BodySubscriber<T> {
    val inputStreamSubscriber = HttpResponse.BodySubscribers.ofInputStream()

    val isGzipContent = responseInfo.headers()
      .allValues(CONTENT_ENCODING_HEADER)
      .contains(CONTENT_ENCODING_GZIP)

    val subscriber = if (isGzipContent) {
      HttpResponse.BodySubscribers.mapping<InputStream?, InputStream?>(inputStreamSubscriber, ::GZIPInputStream)
    }
    else {
      inputStreamSubscriber
    }

    return HttpResponse.BodySubscribers.mapping(subscriber) {
      streamReader(responseInfo, it)
    }
  }
}