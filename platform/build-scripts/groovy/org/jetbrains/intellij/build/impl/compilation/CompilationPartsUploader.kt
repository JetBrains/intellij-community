// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.diagnostic.telemetry.use
import io.opentelemetry.api.trace.Span
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Path

open class CompilationPartsUploader(serverUrl: String) {
  protected val serverUrl = fixServerUrl(serverUrl)

  fun upload(path: String, file: Path, sendHead: Boolean = false): Boolean {
    val url = serverUrl + path.trimStart('/')
    spanBuilder("upload").setAttribute("url", url).setAttribute("path", path).use {
      check(Files.exists(file)) {
        "The file $file does not exist"
      }

      if (sendHead) {
        val code = head(url)
        if (code == HttpURLConnection.HTTP_OK) {
          Span.current().addEvent("file already exist on server, nothing to upload")
          return false
        }
        check(code == HttpURLConnection.HTTP_NOT_FOUND) {
          "HEAD $path responded with unexpected $code"
        }
      }

      httpClient.newCall(Request.Builder().url(url).post(object : RequestBody() {
        override fun contentType() = MEDIA_TYPE_BINARY

        override fun contentLength() = Files.size(file)

        override fun writeTo(sink: BufferedSink) {
          file.source().use(sink::writeAll)
        }
      }).build()).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException("Failed to upload $url: $response")
        }
      }
    }
    return true
  }

  protected fun head(url: String): Int {
    return spanBuilder("head").setAttribute("url", url).use {
      httpClient.head(url).use { it.code }
    }
  }
}

@Suppress("HttpUrlsUsage")
private fun fixServerUrl(serverUrl: String): String {
  var url = serverUrl
  if (!url.startsWith("http://") && !url.startsWith("https://")) {
    url = "http://$url"
  }
  if (!url.endsWith("/")) {
    url += '/'
  }
  return url
}
