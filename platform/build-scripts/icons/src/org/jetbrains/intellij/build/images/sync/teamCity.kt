// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import java.io.File
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

internal fun isNotificationRequired(context: Context) =
  Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    // not weekend
    .let { it != Calendar.SATURDAY && it != Calendar.SUNDAY } &&
  // remind of failure every day
  (isScheduled() && context.isFail() ||
   // or check previous build and notify on fail -> success
   !context.isFail() && teamCityGet("builds?locator=buildType:$BUILD_CONF,count:1")
     .contains("status=\"FAILURE\""))

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
      investigator.isAssigned = true
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
    !investigator.isAssigned && investigator.email != tryToMapEmailFromGitToTeamCity(investigator.email) -> {
      assignInvestigation(Investigator(tryToMapEmailFromGitToTeamCity(investigator.email), investigator.commits), context)
    }
    !investigator.isAssigned && investigator.email != DEFAULT_INVESTIGATOR -> {
      assignInvestigation(Investigator(DEFAULT_INVESTIGATOR, investigator.commits), context)
    }
    else -> investigator
  }
}

private fun tryToMapEmailFromGitToTeamCity(email: String): String {
  val (username, domain) = email.split("@")
  return username.splitNotBlank(".").joinToString(".", transform = String::capitalize) + "@$domain"
}

internal fun thisBuildReportableLink() =
  "${System.getProperty("intellij.icons.report.buildserver")}/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF"

internal fun triggeredBy() = System.getProperty("teamcity.build.triggeredBy.username")
  ?.takeIf { it.isNotBlank() }
  ?.let { teamCityGet("users/username:$it/email") }
  ?.removeSuffix(System.lineSeparator())

internal fun isScheduled() = System.getProperty("teamcity.build.triggeredBy")?.contains("Schedule") == true