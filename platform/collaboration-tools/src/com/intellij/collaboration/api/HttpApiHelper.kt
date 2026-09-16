// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.api.httpclient.CommonHeadersConfigurer
import com.intellij.collaboration.api.httpclient.CompoundRequestConfigurer
import com.intellij.collaboration.api.httpclient.HttpClientFactory
import com.intellij.collaboration.api.httpclient.HttpClientFactoryBase
import com.intellij.collaboration.api.httpclient.HttpClientUtil
import com.intellij.collaboration.api.httpclient.HttpRequestConfigurer
import com.intellij.collaboration.api.httpclient.RequestTimeoutConfigurer
import com.intellij.collaboration.api.httpclient.checkStatusCodeWithLogging
import com.intellij.collaboration.api.httpclient.forceAcceptGzipEncoding
import com.intellij.collaboration.api.httpclient.readBodyWithLogging
import com.intellij.collaboration.api.httpclient.withNewBody
import com.intellij.collaboration.async.awaitInterrupting
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.checkCanceled
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset

/**
 * A helper for building a JDK11 HTTP client based API client that handles things like
 * common headers (ex. auth), logging, content encoding and error handling
 */
@ApiStatus.Experimental
interface HttpApiHelper {

  /**
   * Creates a request builder from the given URI.
   * Pre-configures the builder according to the rules defined by the implementation.
   */
  suspend fun request(uri: URI): HttpRequest.Builder

  /**
   * Sends the given request and awaits a response in a suspended cancellable way.
   * The body handler is used to fully handle the body, no additional handling is done by this method.
   */
  @Throws(IOException::class)
  suspend fun <T> sendAndAwait(request: HttpRequest, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<out T>

  /**
   * Same as [sendAndAwait], but allows a custom [requestName] for logging purposes
   */
  @Throws(IOException::class)
  suspend fun <T> sendAndAwait(request: HttpRequest, requestName: String, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<out T>

  /**
   * Reads the response from the body input stream with a [bodyReader]
   *
   * Takes care of logging, error handling and `gzip` encoding
   *
   * @throws IOException when a connection has failed
   * @throws HttpStatusErrorException when an error status code is received (>400), in this case the body is read as a string and put into the exception
   */
  @Throws(IOException::class, HttpStatusErrorException::class)
  suspend fun <R> handleStreamingResponse(
    requestName: String,
    response: HttpResponse<out InputStream>,
    bodyReader: InputStream.(mimeType: String?, charset: Charset?) -> R,
  ): R
}

/**
 * Creates a request builder from the given URI String.
 */
@Throws(IllegalArgumentException::class)
suspend fun HttpApiHelper.request(uri: String): HttpRequest.Builder {
  return request(URI.create(uri))
}

/**
 * Sends the given request, reads the response from the body input stream with a [bodyReader],
 * taking care of logging and error handling, and returns the response with the [HttpResponse.body] stream mapped to a value of type [T]
 *
 * The request is configured for `gzip` encoding and the `InputStream` passed to [bodyReader] is a decoded response stream
 * if the server returns an encoded response.
 * NB: Any other pre-configured encoding is disabled.
 *
 * @param requestName is used as an identifier when logging
 *
 * @throws IOException when a connection or response processing fails
 * @throws HttpStatusErrorException when an error status code is received (>400), in this case the body is read as a string and put into the exception
 */
@Throws(IOException::class, HttpStatusErrorException::class)
suspend fun <T> HttpApiHelper.sendAndRead(
  request: HttpRequest,
  requestName: String = request.logName(),
  bodyReader: InputStream.(mimeType: String?, charset: Charset?) -> T,
): HttpResponse<T> {
  val actualRequest = request.forceAcceptGzipEncoding()
  val response = sendAndAwait(actualRequest, requestName, HttpResponse.BodyHandlers.ofInputStream())
  checkCanceled()
  val body = handleStreamingResponse(requestName, response, bodyReader)
  return response.withNewBody(body)
}

/**
 * Sends the given request and reads the response from the body input stream with a [bodyReader],
 * taking care of logging and error handling
 *
 * The request is configured for `gzip` encoding and the `InputStream` passed to [bodyReader] is a decoded response stream
 * if the server returns an encoded response.
 * NB: Any other pre-configured encoding is disabled.
 *
 * @throws IOException when a connection or response processing fails
 * @throws HttpStatusErrorException when an error status code is received (>400), in this case the body is read as a string and put into the exception
 */
@Throws(IOException::class, HttpStatusErrorException::class)
suspend fun <T> HttpApiHelper.sendAndReadBody(
  request: HttpRequest,
  bodyReader: InputStream.(mimeType: String?, charset: Charset?) -> T,
): T {
  return sendAndRead(request, request.logName(), bodyReader).body()
}

/**
 * Sends the given request and ignores the response body (except when there's an error or trace logging is enabled)
 *
 * The request is configured for `gzip` encoding.
 * NB: Any other pre-configured encoding is disabled.
 *
 * @throws IOException when a connection has failed
 * @throws HttpStatusErrorException when an error status code is received (>400), in this case the body is read as a string and put into the exception
 */
@Throws(IOException::class, HttpStatusErrorException::class)
suspend fun HttpApiHelper.sendAndAwait(request: HttpRequest) {
  val actualRequest = request.forceAcceptGzipEncoding()
  sendAndReadBody(actualRequest) { _, _ -> }
}

@ApiStatus.Experimental
fun HttpApiHelper(
  logger: Logger = Logger.getInstance(HttpApiHelper::class.java),
  clientFactory: HttpClientFactory = HttpClientFactoryBase(),
  requestConfigurer: HttpRequestConfigurer = defaultRequestConfigurer,
): HttpApiHelper =
  HttpApiHelperImpl(logger, clientFactory, requestConfigurer)

private val defaultRequestConfigurer = CompoundRequestConfigurer(listOf(
  RequestTimeoutConfigurer(),
  CommonHeadersConfigurer()
))

private class HttpApiHelperImpl(
  private val logger: Logger,
  private val clientFactory: HttpClientFactory,
  private val requestConfigurer: HttpRequestConfigurer,
) : HttpApiHelper {

  val client: HttpClient = clientFactory.createClient()

  override suspend fun request(uri: URI): HttpRequest.Builder = HttpRequest.newBuilder(uri).apply {
    requestConfigurer.configureSuspend(this)
  }

  override suspend fun <T> sendAndAwait(request: HttpRequest, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<out T> =
    sendAndAwait(request, request.logName(), bodyHandler)

  override suspend fun <T> sendAndAwait(
    request: HttpRequest,
    requestName: String,
    bodyHandler: HttpResponse.BodyHandler<T>,
  ): HttpResponse<out T> {
    logger.debug(requestName)
    return client.sendAsync(request, bodyHandler).awaitInterrupting()
  }

  override suspend fun <R> handleStreamingResponse(
    requestName: String,
    response: HttpResponse<out InputStream>,
    bodyReader: InputStream.(mimeType: String?, charset: Charset?) -> R,
  ): R {
    response.checkStatusCodeWithLogging(logger, requestName)
    checkCanceled()
    return response.readBodyWithLogging(logger, requestName, bodyReader)
  }
}

fun HttpRequest.logName(): String = HttpClientUtil.getRequestName(this)