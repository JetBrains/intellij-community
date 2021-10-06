// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.lang.Thread.sleep
import java.net.HttpURLConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

private val httpClient = HttpClient.newBuilder()
  .followRedirects(HttpClient.Redirect.ALWAYS)
  .connectTimeout(Duration.ofSeconds(5))
  .build()

fun download(url: String): ByteArray {
  var attemptNumber = 0
  while (true) {
    val request = HttpRequest.newBuilder(URI(url))
      .header("Accept", "application/json")
      .header("Accept-Encoding", "gzip")
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    val encoding = response.headers().firstValue("Content-Encoding").orElse("")
    val content = (if (encoding == "gzip") GZIPInputStream(response.body()) else response.body()).use {
      it.readAllBytes()
    }
    val statusCode = response.statusCode()
    if (statusCode == 200) {
      return content
    }
    else if (attemptNumber > 3 || statusCode < HttpURLConnection.HTTP_INTERNAL_ERROR) {
      // do not attempt again if client error
      throw RuntimeException("Cannot download $url (status=${response.statusCode()}, content=${content.toString(Charsets.UTF_8)})")
    }
    else {
      attemptNumber++
      sleep(attemptNumber * 1000L)
    }
  }
}