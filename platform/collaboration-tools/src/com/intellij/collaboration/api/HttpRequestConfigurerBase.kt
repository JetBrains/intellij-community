// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.util.io.HttpRequests
import java.net.http.HttpRequest
import java.time.Duration

open class HttpRequestConfigurerBase : HttpRequestConfigurer {

  protected open val readTimeoutMillis = HttpRequests.READ_TIMEOUT.toLong()
  protected open val commonHeaders: Map<String, String> =
    mapOf("Accept-Encoding" to "gzip",
          "User-Agent" to "JetBrains IDE")

  override fun configure(builder: HttpRequest.Builder): HttpRequest.Builder = builder
    .timeout(Duration.ofMillis(readTimeoutMillis))
    .apply { commonHeaders.forEach(::header) }
}