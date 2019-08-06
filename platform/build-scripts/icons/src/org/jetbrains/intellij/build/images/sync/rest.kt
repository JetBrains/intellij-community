// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.util.*

internal fun get(path: String, conf: HttpRequestBase.() -> Unit = {}) = rest(HttpGet(path).apply { conf() })

internal fun post(path: String, body: String, conf: HttpRequestBase.() -> Unit = {}) = rest(HttpPost(path).apply {
  entity = StringEntity(body, Charsets.UTF_8)
  conf()
})

private fun rest(request: HttpRequestBase) = HttpClients.createDefault().use {
  val response = it.execute(request)
  val entity = response.entity.asString()
  if (response.statusLine.statusCode != HttpStatus.SC_OK) error("${response.statusLine.statusCode} ${response.statusLine.reasonPhrase} $entity")
  entity
}

internal fun HttpRequestBase.basicAuth(login: String, password: String) {
  addHeader(HttpHeaders.AUTHORIZATION, "Basic ${Base64.getEncoder().encodeToString("$login:$password".toByteArray())}")
}

internal fun HttpRequestBase.tokenAuth(token: String) {
  addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
}

internal fun HttpEntity.asString() = EntityUtils.toString(this, Charsets.UTF_8)