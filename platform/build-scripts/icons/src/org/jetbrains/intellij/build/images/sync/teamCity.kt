// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

internal fun isUnderTeamCity() = BUILD_SERVER != null
private val BUILD_SERVER = System.getProperty("teamcity.serverUrl")
private val BUILD_CONF = System.getProperty("teamcity.buildType.id")
private val BUILD_ID = System.getProperty("teamcity.build.id")

private fun teamCityGet(path: String) = get("$BUILD_SERVER/httpAuth/app/rest/$path") {
  teamCityAuth()
}

private fun teamCityPost(path: String, body: String) = post("$BUILD_SERVER/httpAuth/app/rest/$path", body) {
  teamCityAuth()
  addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_XML.toString())
}

private fun HttpRequestBase.teamCityAuth() {
  basicAuth(System.getProperty("pin.builds.user.name"), System.getProperty("pin.builds.user.password"))
}

private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd'T'HHmmsszzz")
private val NOTIFICATIONS_MIN_INTERVAL_HOURS = System.getProperty("notifications.min.interval.hours")?.toInt() ?: 12

internal fun isNotificationRequired(context: Context): Boolean {
  val calendar = Calendar.getInstance()
  if (calendar.get(Calendar.DAY_OF_WEEK).let {
      it == Calendar.SATURDAY || it == Calendar.SUNDAY
    }) return false
  val request = "builds?locator=buildType:$BUILD_CONF,count:1"
  val intervalStart = DATE_FORMAT.format(with(calendar) {
    add(Calendar.HOUR, -NOTIFICATIONS_MIN_INTERVAL_HOURS)
    time
  }).let { URLEncoder.encode(it, Charsets.UTF_8.name()) }
  val noNotificationsSinceIntervalStart = teamCityGet("$request,sinceDate:$intervalStart").contains("count=\"0\"")
  return noNotificationsSinceIntervalStart &&
         // remind of failure
         (context.isFail() ||
          // or check previous build and notify on fail -> success
          teamCityGet(request).contains("status=\"FAILURE\""))
}

internal val DEFAULT_INVESTIGATOR by lazy {
  System.getProperty("intellij.icons.sync.default.investigator")?.takeIf { it.isNotBlank() } ?: error("Specify default investigator")
}

internal class Investigator(val email: String = DEFAULT_INVESTIGATOR,
                            val commits: Map<File, Collection<CommitInfo>> = emptyMap(),
                            var isAssigned: Boolean = false)

internal fun assignInvestigation(investigator: Investigator, context: Context): Investigator {
  try {
    val assignee = teamCityGet("investigations?locator=buildType:$BUILD_CONF")
    val id = teamCityGet("users/email:${investigator.email}/id")
    if (assignee.contains(id)) {
      log("Investigation is already assigned to ${investigator.email}")
      return investigator
    }
    val text = context.report().let { if (it.isNotEmpty()) "$it, " else it } +
               (if (investigator.commits.isNotEmpty()) "commits: ${investigator.commits.description()}," else "") +
               " build: ${thisBuildReportableLink()}," +
               " see also: https://confluence.jetbrains.com/display/IDEA/Working+with+icons+in+IntelliJ+Platform"
    teamCityPost("investigations", """
      <investigation state="TAKEN">
          <assignee id="$id"/>
          <assignment>
              <text><![CDATA[$text]]></text>
          </assignment>
          <scope>
              <buildTypes count="1">
                  <buildType id="$BUILD_CONF"/>
              </buildTypes>
          </scope>
          <target anyProblem="true"/>
          <resolution type="whenFixed"/>
      </investigation>""".trimIndent())
    investigator.isAssigned = true
    log("Investigation is assigned to ${investigator.email} with message '$text'")
  }
  catch (e: Exception) {
    log("Unable to assign investigation to ${investigator.email}, ${e.message}")
  }
  return when {
    !investigator.isAssigned && investigator.email != investigator.email.toLowerCase() -> {
      assignInvestigation(Investigator(investigator.email.toLowerCase(), investigator.commits), context)
    }
    !investigator.isAssigned && investigator.email != DEFAULT_INVESTIGATOR -> {
      assignInvestigation(Investigator(DEFAULT_INVESTIGATOR, investigator.commits), context)
    }
    else -> investigator
  }
}

internal fun thisBuildReportableLink() =
  "${System.getProperty("intellij.icons.report.buildserver")}/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF"

internal fun triggeredBy() = System.getProperty("teamcity.build.triggeredBy.username")
  ?.takeIf { it.isNotBlank() }
  ?.let { teamCityGet("users/username:$it/email") }
  ?.removeSuffix(System.lineSeparator())