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
  return upsourceRetry {
    get("$UPSOURCE/~rpc/$method$params") {
      upsourceAuthAndLog(method, args)
    }
  }
}

private fun upsourcePost(method: String, args: String) = upsourceRetry {
  post("$UPSOURCE/~rpc/$method", args) {
    upsourceAuthAndLog(method, args)
    addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
  }
}

private fun <T> upsourceRetry(action: () -> T) = retry(action = action, secondsBeforeRetry = 60, doRetry = {
  it.message?.contains("Upsource is down for maintenance") == true
})

private fun HttpRequestBase.upsourceAuthAndLog(method: String, args: String) {
  log("Calling Upsource '$method' with '$args'")
  basicAuth(System.getProperty("upsource.user.name"), System.getProperty("upsource.user.password"))
}

internal sealed class Review(val id: String, val projectId: String?, val url: String)
internal class UpsourceReview(id: String, projectId: String?, url: String) : Review(id, projectId, url)
internal class PlainOldReview(branch: String, projectId: String?) : Review(branch, projectId, branch)
private class Revision(val revisionId: String, val branchHeadLabel: String)

internal fun createReview(projectId: String, branch: String, head: String, commits: Collection<String>): Review {
  val max = 20
  val timeout = 30L
  var revisions = emptyList<Revision>()
  loop@ for (i in 1..max) {
    revisions = try {
      getBranchRevisions(projectId, branch, commits)
    }
    catch (e: Exception) {
      log(e.message ?: e::class.java.canonicalName)
      emptyList()
    }
    when {
      revisions.isNotEmpty() -> break@loop
      i == max -> error("$commits are not found")
      else -> {
        log("Upsource hasn't updated branch list yet. ${i + 1} attempt in ${timeout}s..")
        TimeUnit.SECONDS.sleep(timeout)
      }
    }
  }
  val response = upsourcePost("createReview", """{
      "projectId" : "$projectId",
      "revisions" : [
        ${revisions.joinToString { "\"${it.revisionId}\"" }}
    ]}""")
  val reviewId = extract(response, Regex(""""reviewId":"([^,"]+)""""))
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
  val review = UpsourceReview(reviewId, projectId, asUrl(reviewId, projectId))
  val conf = System.getProperty("teamcity.buildConfName")
  postComment(projectId, review, "$conf. Please review changes and cherry-pick into $head with `Delete the source branch` box checked")
  return review
}

private fun asUrl(reviewId: String, projectId: String) = "$UPSOURCE/$projectId/review/$reviewId"

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
    val userId = userId(email) ?: guessEmail(email).asSequence().map(::userId).filterNotNull().first()
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

private val HUB by lazy { System.getProperty("hub.url") }

private fun userId(email: String): String? {
  log("Calling Hub 'users' with '$email'")
  val response = get("$HUB/api/rest/users?fields=id&top=1&query=email:$email+and+has:verifiedEmail") {
    basicAuth(System.getProperty("upsource.user.name"), System.getProperty("upsource.user.password"))
  }
  return extractOrNull(response, Regex(""""id":"([^,"]+)""""))
}

private fun extract(json: String, regex: Regex) = extractOrNull(json, regex) ?: error(json)
private fun extractOrNull(json: String, regex: Regex) = extractAll(json, regex).lastOrNull()
private fun extractAll(json: String, regex: Regex) = json
  .replace(" ", "")
  .replace(System.lineSeparator(), "").let { str ->
    regex.findAll(str).map { it.groupValues.last() }.toList()
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

// TODO: take commit message from revisions
internal fun getOpenIconsReviewTitles(projectId: String) = upsourceGet("getReviews", """{
    "projectId" : "$projectId",
    "limit": ${Int.MAX_VALUE},
    "query" : "state: open and created-by: {Icons Sync Robot}"
  }""").run {
  val reviews = extractAll(this, Regex(""""reviewId":"([^,"]+)""""))
  val titles = extractAll(this, Regex(""""title":"([^"]+)""""))
  if (reviews.size != titles.size) error("Size of ${reviews} != size of ${titles}")
  reviews.withIndex().associate {
    asUrl(it.value, projectId) to titles[it.index]
  }
}

internal fun closeReview(projectId: String, review: Review) {
  upsourcePost("closeReview", """{
      "isFlagged" : true,
      "reviewId": {
      	"projectId" : "$projectId",
      	"reviewId": "${review.id}"
      }
  }""")
}