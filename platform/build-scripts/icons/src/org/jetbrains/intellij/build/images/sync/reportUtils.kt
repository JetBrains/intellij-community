// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.commons.codec.binary.Base64
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Consumer

internal val BUILD_SERVER = System.getProperty("teamcity.serverUrl")
private val BUILD_CONF = System.getProperty("teamcity.buildType.id")

internal fun report(
  root: File, devIcons: Int, icons: Int, skipped: Int,
  addedByDev: Collection<String>, removedByDev: Collection<String>,
  modifiedByDev: Collection<String>, addedByDesigners: Collection<String>,
  removedByDesigners: Collection<String>, modifiedByDesigners: Collection<String>,
  consistent: Collection<String>, errorHandler: Consumer<String>, doNotify: Boolean
) {
  log("Skipped $skipped dirs")
  fun Collection<String>.logIcons(description: String) = "$size $description${if (size < 100) ": ${joinToString()}" else ""}"
  val report = """
    |$devIcons icons are found in dev repo:
    | ${addedByDev.logIcons("added")}
    | ${removedByDev.logIcons("removed")}
    | ${modifiedByDev.logIcons("modified")}
    |$icons icons are found in icons repo:
    | ${addedByDesigners.logIcons("added")}
    | ${removedByDesigners.logIcons("removed")}
    | ${modifiedByDesigners.logIcons("modified")}
    |${consistent.size} consistent icons in both repos
  """.trimMargin()
  log(report)
  if (doNotify) {
    val success = addedByDev.isEmpty() && removedByDev.isEmpty() && modifiedByDev.isEmpty()
    if (BUILD_SERVER == null) {
      log("TeamCity url is unknown: unable to query last build status for sending notifications and assigning investigations")
    }
    else {
      val investigator = if (!success) assignInvestigation(root, addedByDev, removedByDev, modifiedByDev) else null
      sendNotification(success, investigator)
    }
    if (!success) errorHandler.accept(report)
  }
}

private fun sendNotification(isSuccess: Boolean, investigator: Investigator?) {
  callSafely {
    if (isNotificationRequired(isSuccess)) {
      notifySlackChannel(isSuccess, investigator)
    }
  }
}

private val DEFAULT_INVESTIGATOR by lazy {
  System.getProperty("intellij.icons.sync.default.investigator") ?: error("Specify default investigator")
}

private class Investigator(val email: String, val commits: Collection<String>, var isAssigned: Boolean = false) {
  fun commitsToInvestigate() = if (commits.isNotEmpty()) "see ${commits.joinToString()}" else ""
}

private fun assignInvestigation(root: File,
                                addedByDev: Collection<String>,
                                removedByDev: Collection<String>,
                                modifiedByDev: Collection<String>): Investigator? =
  callSafely {
    val investigations = teamCityGet("investigations?locator=buildType:$BUILD_CONF")
    if (investigations.contains("assignee")) {
      log("Investigation is already assigned")
      null
    }
    else {
      var investigator = (addedByDev.asSequence() + removedByDev.asSequence() + modifiedByDev.asSequence())
        .map { File(root, it).absolutePath }
        .map { latestChangeCommit(it) }
        .filterNotNull()
        .groupBy({ it.committerEmail }, { it.hash })
        .maxBy { it.value.size }
        ?.let { Investigator(it.key, it.value) }
        ?.also { assignInvestigation(it) }
      when {
        investigator != null && !investigator.isAssigned -> {
          investigator = Investigator(DEFAULT_INVESTIGATOR, investigator.commits)
          assignInvestigation(investigator)
        }
        investigator == null -> {
          log("Unable to determine committer email for investigation assignment")
          investigator = Investigator(DEFAULT_INVESTIGATOR, emptyList())
          assignInvestigation(investigator)
        }
      }
      investigator
    }
  }

private fun assignInvestigation(investigator: Investigator) {
  try {
    val id = teamCityGet("users/email:${investigator.email}/id")
    val text = "${investigator.commitsToInvestigate()}.\nhttps://confluence.jetbrains.com/display/IDEA/Working+with+icons+in+IntelliJ+Platform"
    teamCityPost("investigations", """
            |<investigation state="TAKEN">
            |    <assignee id="$id"/>
            |    <assignment>
            |        <text>$text</text>
            |    </assignment>
            |    <scope>
            |        <buildTypes count="1">
            |            <buildType id="$BUILD_CONF"/>
            |        </buildTypes>
            |    </scope>
            |    <target anyProblem="true"/>
            |    <resolution type="whenFixed"/>
            |</investigation>
          """.trimMargin())
    investigator.isAssigned = true
    log("Investigation is assigned to ${investigator.email}, see ${investigator.commits}")
  }
  catch (e: Exception) {
    log("Unable to assign investigation to ${investigator.email}, ${e.message}")
  }
}

private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmsszzz")

private fun isNotificationRequired(isSuccess: Boolean): Boolean {
  val request = "builds?locator=buildType:$BUILD_CONF,count:1"
  return if (isSuccess) {
    // notify on fail -> success
    val previousBuild = teamCityGet(request)
    previousBuild.contains("status=\"FAILURE\"")
  }
  else {
    val dayAgo = DATE_FORMAT.format(Calendar.getInstance().let { calendar ->
      calendar.add(Calendar.HOUR, -12)
      calendar.time
    })
    // remind of failure once per day
    val previousBuild = teamCityGet("$request,sinceDate:${URLEncoder.encode(dayAgo, "UTF-8")}")
    previousBuild.contains("count=\"0\"")
  }
}

private fun teamCityGet(path: String) = get("$BUILD_SERVER/httpAuth/app/rest/$path") {
  teamCityAuth()
}

private fun teamCityPost(path: String, body: String) = post("$BUILD_SERVER/httpAuth/app/rest/$path", body) {
  teamCityAuth()
  addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_XML.toString())
}

private fun HttpRequestBase.teamCityAuth() {
  val authHeader = "${System.getProperty("pin.builds.user.name")}:${System.getProperty("pin.builds.user.password")}"
  addHeader(HttpHeaders.AUTHORIZATION, "Basic ${Base64.encodeBase64String(authHeader.toByteArray())}")
}

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")
private val BUILD_ID = System.getProperty("teamcity.build.id")
private val INTELLIJ_ICONS_SYNC_RUN_CONF = System.getProperty("intellij.icons.sync.run.conf")

private fun notifySlackChannel(isSuccess: Boolean, investigator: Investigator?) {
  val investigation = when {
    investigator == null -> ""
    investigator.isAssigned -> "Investigation is assigned to ${investigator.email}\n"
    else -> "Unable to assign investigation to ${investigator.email}\n"
  }
  val text = "*${System.getProperty("teamcity.buildConfName")}* " +
             (if (isSuccess) ":white_check_mark:" else ":scream:") + "\n" + investigation +
             (if (!isSuccess) "Use 'Icons processing/*$INTELLIJ_ICONS_SYNC_RUN_CONF*' IDEA Ultimate run configuration\n" else "") +
             "<$BUILD_SERVER/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF|See build log>"
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}

private fun get(path: String, conf: HttpRequestBase.() -> Unit = {}) = rest(HttpGet(path).apply { conf() })

private fun post(path: String, body: String, conf: HttpRequestBase.() -> Unit = {}) = rest(HttpPost(path).apply {
  conf()
  entity = StringEntity(body, Charsets.UTF_8)
})

private fun rest(request: HttpRequestBase) = HttpClients.createDefault().use {
  val response = it.execute(request)
  val entity = EntityUtils.toString(response.entity, Charsets.UTF_8)
  if (response.statusLine.statusCode != 200) error("${response.statusLine.statusCode} ${response.statusLine.reasonPhrase} $entity")
  entity
}