// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import java.net.URLEncoder

private val UPSOURCE = System.getProperty("upsource.url")

private fun upsourceGet(method: String, args: String): String {
  val params = if (args.isEmpty()) "" else "?params=${URLEncoder.encode(args, Charsets.UTF_8.name())}"
  return get("$UPSOURCE/~rpc/$method$params") {
    upsourceAuthAndLog()
  }
}

private fun upsourcePost(method: String, args: String) = post("$UPSOURCE/~rpc/$method", args) {
  upsourceAuthAndLog()
  addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
}

private fun HttpRequestBase.upsourceAuthAndLog() {
  log("Calling $uri${if (this is HttpEntityEnclosingRequest && entity != null) " with ${entity.asString()}" else ""}")
  basicAuth(System.getProperty("upsource.user.name"), System.getProperty("upsource.user.password"))
}

internal class Review(val id: String, val url: String)

@Suppress("ReplaceSingleLineLet")
internal fun createReview(projectId: String, vararg revisions: String): Review {
  val reviewId = upsourcePost("createReview", """{
    "projectId" : "$projectId",
    "revisions" : [
      ${revisions.joinToString { "\"$it\"" }}
    ]}""".trimIndent())
    .let { extract(it, Regex(""""reviewId":\{([^}]+)""")) }
    .let { extract(it, Regex(""""reviewId":"([^,"]+)"""")) }
  return Review(reviewId, "$UPSOURCE/$projectId/review/$reviewId")
}

internal fun addParticipantToReview(projectId: String, review: Review, email: String) {
  val invitation = upsourceGet("inviteUser", """{"projectId":"$projectId","email":"$email"}""")
  val userId = extract(invitation, Regex(""""userId":"([^,"]+)""""))
  upsourcePost("addParticipantToReview", """{
    "reviewId" : {
      "projectId" : "$projectId",
      "reviewId" : "${review.id}"
    },
    "participant" : {
      "userId" : "$userId",
      "role" : 2
     }
  }""".trimIndent())
}

private fun extract(json: String, regex: Regex) = json
  .replace(" ", "")
  .replace(System.lineSeparator(), "").let {
    regex.find(it)?.groupValues?.lastOrNull() ?: error(it)
  }