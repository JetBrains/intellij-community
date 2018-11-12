// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

private val UPSOURCE = System.getProperty("upsource.url")

private fun upsourceGet(method: String, args: String): String {
  val params = if (args.isEmpty()) "" else "?params=${URLEncoder.encode(args, Charsets.UTF_8.name())}"
  return get("$UPSOURCE/~rpc/$method$params") {
    upsourceAuthAndLog(method, args)
  }
}

private fun upsourcePost(method: String, args: String) = post("$UPSOURCE/~rpc/$method", args) {
  upsourceAuthAndLog(method, args)
  addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
}

private fun HttpRequestBase.upsourceAuthAndLog(method: String, args: String) {
  log("Calling Upsource '$method' with '$args'")
  basicAuth(System.getProperty("upsource.user.name"), System.getProperty("upsource.user.password"))
}

internal class Review(val id: String, val url: String)

@Suppress("ReplaceSingleLineLet")
internal fun createReview(projectId: String, branch: String, commits: Collection<String>): Review {
  val max = 20
  val timeout = 30L
  var revisions = emptyList<String>()
  loop@ for (i in 1..max) {
    revisions = getBranchRevisions(projectId, branch, commits)
    when {
      revisions.isNotEmpty() -> break@loop
      i == max -> error("$commits are not found")
      else -> {
        log("Upsource hasn't updated branch list yet. ${i + 1} attempt in ${timeout}s..")
        TimeUnit.SECONDS.sleep(timeout)
      }
    }
  }
  val reviewId = upsourcePost("createReview", """{
      "projectId" : "$projectId",
      "revisions" : [
        ${revisions.joinToString { "\"$it\"" }}
    ]}""")
    .let { extract(it, Regex(""""reviewId":\{([^}]+)""")) }
    .let { extract(it, Regex(""""reviewId":"([^,"]+)"""")) }
  upsourcePost("startBranchTracking", """{
    "branch" : "$branch",
    "reviewId" : {
      "projectId" : "$projectId",
      "reviewId" : "$reviewId"
    }
  }""")
  return Review(reviewId, "$UPSOURCE/$projectId/review/$reviewId")
}

private fun getBranchRevisions(projectId: String, branch: String, commits: Collection<String>) = commits.parallelStream().map {
  extractOrNull(upsourceGet("getRevisionsListFiltered", """{
      "query" : "branch: $branch $it",
      "projectId" : "$projectId",
      "limit" : 1
    }"""), Regex(""""revisionId":"([^,"]+)""""))
}.filter(Objects::nonNull).map { it as String }.toList()

internal fun addReviewer(projectId: String, review: Review, email: String) {
  try {
    val userId = userId(email, projectId)
    upsourcePost("addParticipantToReview", """{
      "reviewId" : {
        "projectId" : "$projectId",
        "reviewId" : "${review.id}"
      },
      "participant" : {
        "userId" : "$userId",
        "role" : 2
       }
    }""")
  }
  catch (e: Exception) {
    e.printStackTrace()
    if (email != DEFAULT_INVESTIGATOR) addReviewer(projectId, review, DEFAULT_INVESTIGATOR)
  }
}

private fun userId(email: String, projectId: String): String {
  val invitation = upsourceGet("inviteUser", """{"projectId":"$projectId","email":"$email"}""")
  return extract(invitation, Regex(""""userId":"([^,"]+)""""))
}

private fun extract(json: String, regex: Regex) = extractOrNull(json, regex) ?: error(json)

private fun extractOrNull(json: String, regex: Regex) = json
  .replace(" ", "")
  .replace(System.lineSeparator(), "").let { str ->
    regex.findAll(str).map { it.groupValues.last() }.lastOrNull()
  }

internal fun postComment(projectId: String, review: Review, comment: String) {
  upsourcePost("createDiscussion", """{
      "projectId" : "$projectId",
      "reviewId": {
      	"projectId" : "$projectId",
      	"reviewId": "${review.id}"
      },
      "text" : "$comment",
      "anchor" : {}
  }""")
}