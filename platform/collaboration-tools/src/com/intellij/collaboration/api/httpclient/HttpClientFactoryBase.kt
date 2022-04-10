// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.util.io.HttpRequests
import java.net.ProxySelector
import java.net.http.HttpClient
import java.time.Duration

open class HttpClientFactoryBase : HttpClientFactory {

  protected open val useProxy = true
  protected open val connectionTimeoutMillis = HttpRequests.CONNECTION_TIMEOUT.toLong()

  override fun createClient(): HttpClient =
    HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .proxy(if (useProxy) ProxySelector.getDefault() else HttpClient.Builder.NO_PROXY)
      .connectTimeout(Duration.ofMillis(connectionTimeoutMillis))
      .executor(ProcessIOExecutorService.INSTANCE)
      .build()
}