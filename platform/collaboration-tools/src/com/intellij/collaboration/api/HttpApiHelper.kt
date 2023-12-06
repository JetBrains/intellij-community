// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.api.httpclient.*
import com.intellij.collaboration.api.httpclient.HttpClientUtil.checkStatusCodeWithLogging
import com.intellij.collaboration.api.httpclient.HttpClientUtil.inflateAndReadWithErrorHandlingAndLogging
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
  /**
   * Creates a request builder from the given URI String.
   */
  fun request(uri: String): HttpRequest.Builder

  /**
   * Creates a request builder from the given URI.
   */
  fun request(uri: URI): HttpRequest.Builder

  /**
   * Sends the given request and awaits a response in a suspended cancellable way.
   * The body handler is used to fully handle the body, no additional handling is done by this method.
   */
  suspend fun <T> sendAndAwaitCancellable(request: HttpRequest, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<out T>

  /**
   * Sends the given request and awaits a response in a suspended cancellable way.
   * Different from the overloaded function with a [java.net.http.HttpResponse.BodyHandler] parameter,
   * this function provides an inflating, logging body handler with no additional mapping of the body.
   */
  suspend fun sendAndAwaitCancellable(request: HttpRequest): HttpResponse<out Unit>

  /**
   * Sends the given request and awaits a response in a suspended cancellable way.
   * Maps the body of the response to an [Image] object.
   */
  suspend fun loadImage(request: HttpRequest): HttpResponse<out Image>
}

@ApiStatus.Experimental
fun HttpApiHelper(logger: Logger = Logger.getInstance(HttpApiHelper::class.java),
                  clientFactory: HttpClientFactory = HttpClientFactoryBase(),
                  requestConfigurer: HttpRequestConfigurer = defaultRequestConfigurer,
                  errorCollector: suspend (Throwable) -> Unit = {}): HttpApiHelper =
  HttpApiHelperImpl(logger, clientFactory, requestConfigurer, errorCollector)

private val defaultRequestConfigurer = CompoundRequestConfigurer(listOf(
  RequestTimeoutConfigurer(),
  CommonHeadersConfigurer()
))

private class HttpApiHelperImpl(
  private val logger: Logger,
  private val clientFactory: HttpClientFactory,
  private val requestConfigurer: HttpRequestConfigurer,
  private val errorCollector: suspend (Throwable) -> Unit
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
    catch (e: Throwable) {
      errorCollector(e)
      throw e
    }
  }

  override suspend fun sendAndAwaitCancellable(request: HttpRequest): HttpResponse<out Unit> =
    sendAndAwaitCancellable(request, inflateAndReadWithErrorHandlingAndLogging(logger, request) { _, _ -> })

  override suspend fun loadImage(request: HttpRequest): HttpResponse<out Image> {
    val bodyHandler = InflatedStreamReadingBodyHandler { responseInfo, stream ->
      checkStatusCodeWithLogging(logger, request.logName(), responseInfo.statusCode(), stream)
      ImageIO.read(stream)
    }
    return sendAndAwaitCancellable(request, bodyHandler)
  }
}

fun HttpRequest.logName(): String = "Request ${method()} ${uri()}"