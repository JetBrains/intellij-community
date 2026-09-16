// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.io.sendHttpRequest
import org.jetbrains.intellij.build.io.withHttpClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import kotlin.time.Duration.Companion.seconds

internal fun post(path: String, body: String, mediaType: String?, conf: HttpRequest.Builder.() -> Unit = {}): String {
  val requestBuilder = HttpRequest.newBuilder(URI(path)).POST(HttpRequest.BodyPublishers.ofString(body))
  if (mediaType != null) requestBuilder.header("Content-Type", "$mediaType; charset=utf-8")
  requestBuilder.conf()
  return rest(requestBuilder.build())
}

internal fun rest(request: HttpRequest): String {
  return withHttpClient(connectTimeout = 10.seconds, version = HttpClient.Version.HTTP_1_1) { client ->
    val response = sendHttpRequest(client, request, timeout = 10.seconds)
    val entity = response.body()
    check(response.statusCode() == 200) { "${response.statusCode()} $entity" }
    entity
  }
}
