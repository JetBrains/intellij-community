// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal val MEDIA_TYPE_BINARY = "application/octet-stream".toMediaType()

internal fun OkHttpClient.head(url: String): Response {
  return newCall(Request.Builder().url(url).head().build()).execute()
}

internal fun OkHttpClient.get(url: String): Response {
  return newCall(Request.Builder().url(url).build()).execute()
}

internal inline fun <T> Response.useSuccessful(task: (Response) -> T): T {
  return use { response ->
    if (response.isSuccessful) {
      task(response)
    }
    else {
      throw IOException("Unexpected code $response")
    }
  }
}

internal val httpClient by lazy {
  OkHttpClient.Builder()
    .addInterceptor { chain ->
      chain.proceed(chain.request()
                      .newBuilder()
                      .header("User-Agent", "IJ Builder")
                      .build())
    }
    .addInterceptor { chain ->
      val request = chain.request()
      var response = chain.proceed(request)
      var tryCount = 0
      while (response.code >= 500 && tryCount < 3) {
        response.close()
        tryCount++
        response = chain.proceed(request)
      }
      response
    }
    .followRedirects(true)
    .build()
}

@Suppress("HttpUrlsUsage")
internal fun toUrlWithTrailingSlash(serverUrl: String): String {
  var result = serverUrl
  if (!result.startsWith("http://") && !result.startsWith("https://")) {
    result = "http://$result"
  }
  if (!result.endsWith('/')) {
    result += '/'
  }
  return result
}