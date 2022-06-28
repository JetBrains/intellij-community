// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import java.net.http.HttpRequest

class CompoundRequestConfigurer(private val configurers: List<HttpRequestConfigurer>) : HttpRequestConfigurer {

  constructor(vararg configurers: HttpRequestConfigurer) : this(configurers.asList())

  override fun configure(builder: HttpRequest.Builder): HttpRequest.Builder {
    configurers.forEach { it.configure(builder) }
    return builder
  }
}