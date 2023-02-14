// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import java.net.http.HttpRequest

open class CommonHeadersConfigurer : HttpRequestConfigurer {

  protected open val commonHeaders: Map<String, String> =
    mapOf("Accept-Encoding" to "gzip",
          "User-Agent" to "JetBrains IDE")

  final override fun configure(builder: HttpRequest.Builder): HttpRequest.Builder = builder
    .apply { commonHeaders.forEach(::header) }
}