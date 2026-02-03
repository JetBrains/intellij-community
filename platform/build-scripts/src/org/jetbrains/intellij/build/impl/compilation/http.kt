// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

internal val httpClient: OkHttpClient by lazy {
  val timeout = 1L
  val unit = TimeUnit.MINUTES
  OkHttpClient.Builder()
    .connectTimeout(timeout, unit)
    .writeTimeout(timeout, unit)
    .readTimeout(timeout, unit)
    .dispatcher(Dispatcher().apply {
      // we upload/download to the same host - increase `maxRequestsPerHost`
      //maxRequestsPerHost = Runtime.getRuntime().availableProcessors().coerceIn(5, 16)
      //... but in the same time it can increase the bill for ALB, so, leave it as is
    })
    .addInterceptor { chain ->
      var request = chain.request()
      if (request.header("User-Agent").isNullOrBlank()) {
        request = request.newBuilder().header("User-Agent", "IJ Builder").build()
      }
      chain.proceed(request)
    }
    .addInterceptor { chain ->
      val request = chain.request()
      var response: Response? = null
      var error: IOException? = null
      val maxTryCount = 3
      var tryCount = 0
      do {
        response?.close()
        response = try {
          chain.proceed(request)
        }
        catch (e: IOException) {
          if (error == null) {
            error = IOException("$maxTryCount attempts to ${request.method} ${request.url} failed")
          }
          error.addSuppressed(e)
          null
        }
        tryCount++
      }
      while ((response == null || response.code >= 500) && tryCount < maxTryCount)
      response ?: throw error ?: IllegalStateException()
    }
    .followRedirects(true)
    .build()
}

@ExperimentalCoroutinesApi
internal suspend fun Call.executeAsync(): Response {
  return suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
      this.cancel()
    }
    enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        continuation.resumeWithException(e)
      }

      override fun onResponse(call: Call, response: Response) {
        continuation.resume(response) {
          response.closeQuietly()
        }
      }
    })
  }
}