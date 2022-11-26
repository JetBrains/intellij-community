// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.api.httpclient.*
import com.intellij.collaboration.api.httpclient.HttpClientUtil.checkStatusCodeWithLogging
import com.intellij.collaboration.api.httpclient.response.CancellableWrappingBodyHandler
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import org.jetbrains.annotations.ApiStatus
import java.awt.Image
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.imageio.ImageIO

@ApiStatus.Experimental
interface HttpApiHelper {
  fun request(uri: String): HttpRequest.Builder
  fun request(uri: URI): HttpRequest.Builder

  suspend fun <T> sendAndAwaitCancellable(request: HttpRequest, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<out T>

  suspend fun loadImage(request: HttpRequest): HttpResponse<out Image>
}

@ApiStatus.Experimental
fun HttpApiHelper(logger: Logger = Logger.getInstance(HttpApiHelper::class.java),
                  clientFactory: HttpClientFactory = HttpClientFactoryBase(),
                  requestConfigurer: HttpRequestConfigurer = defaultRequestConfigurer): HttpApiHelper =
  HttpApiHelperImpl(logger, clientFactory, requestConfigurer)

private val defaultRequestConfigurer = CompoundRequestConfigurer(listOf(
  RequestTimeoutConfigurer(),
  CommonHeadersConfigurer()
))

private class HttpApiHelperImpl(
  private val logger: Logger,
  private val clientFactory: HttpClientFactory,
  private val requestConfigurer: HttpRequestConfigurer
) : HttpApiHelper {

  val client: HttpClient
    get() = clientFactory.createClient()

  override fun request(uri: String): HttpRequest.Builder = request(URI.create(uri))

  override fun request(uri: URI): HttpRequest.Builder = HttpRequest.newBuilder(uri).apply(requestConfigurer::configure)

  override suspend fun <T> sendAndAwaitCancellable(request: HttpRequest, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<out T> {
    val cancellableBodyHandler = CancellableWrappingBodyHandler(bodyHandler)
    return try {
      logger.debug(request.logName())
      client.sendAsync(request, cancellableBodyHandler).await()
    }
    catch (ce: CancellationException) {
      cancellableBodyHandler.cancel()
      throw ce
    }
  }

  override suspend fun loadImage(request: HttpRequest): HttpResponse<out Image> {
    val bodyHandler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      checkStatusCodeWithLogging(logger, request.logName(), responseInfo.statusCode(), stream)
      ImageIO.read(stream)
    }
    return sendAndAwaitCancellable(request, bodyHandler)
  }
}

fun HttpRequest.logName(): String = "Request ${method()} ${uri()}"