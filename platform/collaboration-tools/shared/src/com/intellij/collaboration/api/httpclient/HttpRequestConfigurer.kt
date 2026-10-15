// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import java.net.http.HttpRequest

interface HttpRequestConfigurer {
  @Deprecated("Use suspend version", ReplaceWith("configureSuspend"))
  fun configure(builder: HttpRequest.Builder): HttpRequest.Builder = builder
  suspend fun configureSuspend(builder: HttpRequest.Builder): HttpRequest.Builder = configure(builder)
}