// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.api.httpclient.HttpClientFactory
import com.intellij.collaboration.api.httpclient.HttpRequestConfigurer
import com.intellij.collaboration.api.httpclient.response.CancellableWrappingBodyHandler
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

abstract class HttpApiClient {

  abstract val clientFactory: HttpClientFactory
  abstract val requestConfigurer: HttpRequestConfigurer

  @Suppress("SSBasedInspection")
  abstract val logger: Logger

  val client: HttpClient get() = clientFactory.createClient()

  fun request(uri: String): HttpRequest.Builder =
    request(URI.create(uri))

  fun request(uri: URI): HttpRequest.Builder =
    HttpRequest.newBuilder(uri).apply(requestConfigurer::configure)

  suspend fun <T> sendAndAwaitCancellable(request: HttpRequest, bodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
    val cancellableBodyHandler = CancellableWrappingBodyHandler(bodyHandler)
    return try {
      client.sendAsync(request, cancellableBodyHandler).await()
    }
    catch (ce: CancellationException) {
      cancellableBodyHandler.cancel()
      throw ce
    }
  }

  companion object {
    @JvmStatic
    fun HttpRequest.logName(): String = "Request ${method()} ${uri()}"
  }
}