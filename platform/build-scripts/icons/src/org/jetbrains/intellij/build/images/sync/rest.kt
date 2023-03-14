// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images.sync

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val client by lazy {
  OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build()
}

internal fun post(path: String, body: String, mediaType: MediaType?, conf: Request.Builder.() -> Unit = {}): String {
  val requestBuilder = Request.Builder().url(path).post(body.toRequestBody(mediaType))
  requestBuilder.conf()
  return rest(requestBuilder.build())
}

internal fun rest(request: Request): String {
  client.newCall(request).execute().use { response ->
    val entity = response.body.string()
    check(response.code == 200) { "${response.code} ${response.message} $entity" }
    return entity
  }
}