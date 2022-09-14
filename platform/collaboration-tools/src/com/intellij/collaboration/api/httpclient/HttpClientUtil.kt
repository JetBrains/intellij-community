// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpApiClient
import com.intellij.collaboration.api.HttpApiClient.Companion.logName
import com.intellij.collaboration.api.httpclient.HttpClientUtil.imageBodyHandler
import com.intellij.openapi.diagnostic.Logger
import java.awt.Image
import java.io.InputStream
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.util.zip.GZIPInputStream

object HttpClientUtil {

  private const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  private const val CONTENT_ENCODING_GZIP = "gzip"

  const val CONTENT_TYPE_HEADER = "Content-Type"
  const val CONTENT_TYPE_JSON = "application/json"

  fun gzipInflatingBodySubscriber(responseInfo: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber<InputStream> {
    val inputStreamSubscriber = HttpResponse.BodySubscribers.ofInputStream()

    val gzipContent = responseInfo.headers()
      .allValues(CONTENT_ENCODING_HEADER)
      .contains(CONTENT_ENCODING_GZIP)

    return if (gzipContent) {
      HttpResponse.BodySubscribers.mapping(inputStreamSubscriber, ::GZIPInputStream)
    }
    else {
      inputStreamSubscriber
    }
  }

  fun imageBodyHandler(logger: Logger, request: HttpRequest): BodyHandler<Image> = object : ImageBodyHandler(request) {

    override fun read(bodyStream: InputStream): Image {
      logger.debug("${request.logName()} : Success")
      return super.read(bodyStream)
    }

    override fun handleError(statusCode: Int, errorBody: String): Nothing {
      logger.debug("${request.logName()} : Error ${statusCode}")
      if (logger.isTraceEnabled) {
        logger.trace("${request.logName()} : Response body: $errorBody")
      }
      super.handleError(statusCode, errorBody)
    }
  }
}

suspend fun HttpApiClient.loadImage(request: HttpRequest): HttpResponse<Image> =
  sendAndAwaitCancellable(request, imageBodyHandler(logger, request))