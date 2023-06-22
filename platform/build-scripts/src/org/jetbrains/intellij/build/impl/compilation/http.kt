// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jetbrains.intellij.build.NoMoreRetriesException
import java.io.IOException
import java.util.concurrent.TimeUnit

internal val MEDIA_TYPE_BINARY = "application/octet-stream".toMediaType()

internal fun OkHttpClient.head(url: String, authHeader: String): Int {
  return newCall(Request.Builder().url(url).head()
                   .header("Authorization", authHeader)
                   .build()).execute().use { response ->
    if (response.code != 200 && response.code != 404) {
      throw IOException("Unexpected code $response")
    }
    response.code
  }
}

internal fun <T> OkHttpClient.get(url: String, authHeader: String, task: (Response) -> T): T {
  return newCall(Request.Builder().url(url)
                   .header("Authorization", authHeader)
                   .build()).execute().useSuccessful(task)
}

internal inline fun <T> Response.useSuccessful(task: (Response) -> T): T {
  return use { response ->
    when {
      response.isSuccessful -> task(response)
      response.code == 404 -> throw NoMoreRetriesException("Unexpected code $response")
      else -> throw IOException("Unexpected code $response")
    }
  }
}

internal val httpClient: OkHttpClient by lazy {
  val timeout = 1L
  val unit = TimeUnit.MINUTES
  OkHttpClient.Builder()
    .connectTimeout(timeout, unit)
    .writeTimeout(timeout, unit)
    .readTimeout(timeout, unit)
    .addInterceptor { chain ->
      chain.proceed(chain.request()
                      .newBuilder()
                      .header("User-Agent", "IJ Builder")
                      .build())
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