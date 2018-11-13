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
internal val UPSOURCE_ICONS_PROJECT_ID = System.getProperty("intellij.icons.upsource.project.id")
internal val UPSOURCE_DEV_PROJECT_ID = System.getProperty("intellij.icons.upsource.dev.project.id")

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

internal sealed class Review(val id: String, val projectId: String?, val url: String)
internal class UpsourceReview(id: String, projectId: String?, url: String) : Review(id, projectId, url)
internal class PlainOldReview(branch: String, projectId: String?) : Review(branch, projectId, branch)
private class Revision(val revisionId: String, val branchHeadLabel: String)

@Suppress("ReplaceSingleLineLet")
internal fun createReview(projectId: String, branch: String, commits: Collection<String>): Review {
  val max = 20
  val timeout = 30L
  var revisions = emptyList<Revision>()
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
        ${revisions.joinToString { "\"${it.revisionId}\"" }}
    ]}""")
    .let { extract(it, Regex(""""reviewId":\{([^}]+)""")) }
    .let { extract(it, Regex(""""reviewId":"([^,"]+)"""")) }
  // Revisions may be originated from many repositories
  // but Upsource cannot track more than one branch so any will suffice
  val branchHeadLabel = revisions.first().branchHeadLabel
  upsourcePost("startBranchTracking", """{
    "branch" : "$branchHeadLabel",
    "reviewId" : {
      "projectId" : "$projectId",
      "reviewId" : "$reviewId"
    }
  }""")
  return UpsourceReview(reviewId, projectId, "$UPSOURCE/$projectId/review/$reviewId")
}

private fun getBranchRevisions(projectId: String, branch: String, commits: Collection<String>) = commits.parallelStream().map {
  val response = upsourceGet("getRevisionsListFiltered", """{
      "query" : "branch: $branch $it",
      "projectId" : "$projectId",
      "limit" : 1
    }""")
  val revisionId = extractOrNull(response, Regex(""""revisionId":"([^,"]+)""""))
  if (revisionId != null) {
    val branchHeadLabel = extractOrNull(response, Regex(""""branchHeadLabel":\["([^,"]+)"]""")) ?: branch
    Revision(revisionId, branchHeadLabel)
  }
  else null
}.filter(Objects::nonNull).map { it as Revision }.toList()

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