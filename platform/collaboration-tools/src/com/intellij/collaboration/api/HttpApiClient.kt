// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.api.httpclient.HttpClientFactory
import com.intellij.collaboration.api.httpclient.HttpRequestConfigurer
import com.intellij.collaboration.api.httpclient.ImageBodyHandler
import com.intellij.collaboration.api.httpclient.sendAndAwaitCancellable
import com.intellij.openapi.diagnostic.Logger
import java.awt.Image
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface HttpApiClient {

  val clientFactory: HttpClientFactory
  val requestConfigurer: HttpRequestConfigurer
  val logger: Logger

  val client: HttpClient get() = clientFactory.createClient()

  fun request(uri: String): HttpRequest.Builder =
    request(URI.create(uri))

  fun request(uri: URI): HttpRequest.Builder =
    HttpRequest.newBuilder(uri).apply(requestConfigurer::configure)


  suspend fun loadImage(request: HttpRequest): HttpResponse<Image> =
    client.sendAndAwaitCancellable(request, imageBodyHandler(request))

  private fun imageBodyHandler(request: HttpRequest): HttpResponse.BodyHandler<Image> = object : ImageBodyHandler(request) {

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


  companion object {
    @JvmStatic
    fun HttpRequest.logName(): String = "Request ${method()} ${uri()}"
  }
}