// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import org.jetbrains.intellij.build.tasks.tracer
import org.jetbrains.intellij.build.tasks.use
import java.io.ByteArrayOutputStream
import java.lang.Thread.sleep
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream

private val httpClient by lazy {
  HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(5))
    .build()
}

fun download(url: String): ByteArray {
  tracer.spanBuilder("download").setAttribute("url", url).startSpan().use {
    var attemptNumber = 0
    while (true) {
      val request = HttpRequest.newBuilder(URI(url))
        .header("Accept", "application/json")
        .header("Accept-Encoding", "gzip")
        .build()

      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
      val statusCode = response.statusCode()
      val encoding = response.headers().firstValue("Content-Encoding").orElse("")
      // readAllBytes doesn't work due to incorrect assert in HttpResponseInputStream
      val byteOut = ByteArrayOutputStream()
      (if (encoding == "gzip") GZIPInputStream(response.body()) else response.body()).use {
        it.transferTo(byteOut)
      }
      val content = byteOut.toByteArray()

      if (statusCode == 200) {
        return content
      }
      else if (attemptNumber > 3 || statusCode < 500) {
        // do not attempt again if client error
        throw RuntimeException("Cannot download $url (status=${response.statusCode()}, content=${content.toString(Charsets.UTF_8)})")
      }
      else {
        attemptNumber++
        sleep(attemptNumber * 1_000L)
      }
    }
  }

  throw IllegalStateException("must be unreachable")
}