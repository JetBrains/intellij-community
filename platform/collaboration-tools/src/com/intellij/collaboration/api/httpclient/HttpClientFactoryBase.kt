// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.util.io.HttpRequests
import com.intellij.util.net.PlatformHttpClient
import java.net.ProxySelector
import java.net.http.HttpClient

open class HttpClientFactoryBase : HttpClientFactory {

  protected open val useProxy = true
  protected open val connectionTimeoutMillis = HttpRequests.CONNECTION_TIMEOUT.toLong()

  override fun createClient(): HttpClient =
    PlatformHttpClient.clientBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .proxy(if (useProxy) ProxySelector.getDefault() else HttpClient.Builder.NO_PROXY)
      .build()
}