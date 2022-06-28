// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.HttpStatusErrorException
import com.intellij.collaboration.api.httpclient.response.CancellableWrappingBodyHandler
import com.intellij.collaboration.api.httpclient.response.InflatingInputStreamBodyHandler
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.text.nullize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler

object HttpClientUtil {

  const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP = "gzip"

  const val CONTENT_TYPE_HEADER = "Content-Type"
  const val CONTENT_TYPE_JSON = "application/json"

  private val LOG = thisLogger()

  fun checkResponse(response: HttpResponse<InputStream>) {
    val request = response.request()
    val statusCode = response.statusCode()
    if (statusCode < 400) {
      LOG.debug("Request: ${request.method()} ${request.uri()} : Success ${statusCode}")
      return
    }

    val errorText = response.body().reader().readText().nullize()
    LOG.debug("Request: ${request.method()} ${request.uri()} : Error ${statusCode} body:\n${errorText}")

    throw HttpStatusErrorException(request.method(), request.uri().toString(), statusCode, errorText)
  }

  fun inflatedInputStreamBodyHandler(): BodyHandler<InputStream> = InflatingInputStreamBodyHandler()
}

suspend fun <T> HttpClient.sendAndAwaitCancellable(request: HttpRequest, bodyHandler: BodyHandler<T>): HttpResponse<T> {
  val cancellableBodyHandler = CancellableWrappingBodyHandler(bodyHandler)
  return try {
    sendAsync(request, cancellableBodyHandler).await()
  }
  catch (ce: CancellationException) {
    cancellableBodyHandler.cancel()
    throw ce
  }
}