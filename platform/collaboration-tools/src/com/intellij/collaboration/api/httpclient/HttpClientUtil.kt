// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.collaboration.api.httpclient.response.CancellableWrappingBodyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import java.io.InputStream
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.util.zip.GZIPInputStream

object HttpClientUtil {

  const val CONTENT_ENCODING_HEADER = "Content-Encoding"
  const val CONTENT_ENCODING_GZIP = "gzip"

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