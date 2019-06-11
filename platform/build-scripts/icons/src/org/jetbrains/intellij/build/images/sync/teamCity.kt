// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType

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

internal val DEFAULT_INVESTIGATOR by lazy {
  System.getProperty("intellij.icons.sync.default.investigator")?.takeIf { it.isNotBlank() } ?: error("Specify default investigator")
}

internal class Investigator(val email: String, var isAssigned: Boolean = false)

internal fun assignInvestigation(investigator: Investigator): Investigator {
  val report = "Unexpected error, please investigate."
  assignInvestigation(investigator, report)
  if (!investigator.isAssigned) {
    var nextAttempts = guessEmail(investigator.email)
    if (!isInvestigationAssigned()) nextAttempts += DEFAULT_INVESTIGATOR
    nextAttempts.forEach {
      if (it != investigator.email) {
        val next = Investigator(it)
        assignInvestigation(next, report)
        if (next.isAssigned) return next
      }
    }
  }
  return investigator
}

private val investigation by lazy {
  teamCityGet("investigations?locator=buildType:$BUILD_CONF")
}

private fun isInvestigationAssigned() = with(investigation) {
  contains("assignee") && !contains("GIVEN_UP")
}

private fun assignInvestigation(investigator: Investigator, report: String) {
  try {
    val id = teamCityGet("users/email:${investigator.email}/id")
    if (investigation.contains(id)) {
      log("Investigation is already assigned to ${investigator.email}")
      investigator.isAssigned = true
      return
    }
    val text = (if (report.isNotEmpty()) "$report " else report) +
               "Build: ${thisBuildReportableLink()}, " +
               "see also: https://confluence.jetbrains.com/display/IDEA/Working+with+icons+in+IntelliJ+Platform, https://jetbrains.team/blog/1yjtD11nVsDA"
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
}

internal fun thisBuildReportableLink() =
  "${System.getProperty("intellij.icons.report.buildserver")}/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF"

internal fun triggeredBy() =
  System.getProperty("teamcity.build.triggeredBy.username")
    ?.takeIf(String::isNotBlank)
    ?.let { teamCityGet("users/username:$it/email") }
    ?.removeSuffix(System.lineSeparator())
    ?.takeIf { triggeredByName != null }
    ?.let { email -> TriggeredBy(triggeredByName, email) }
  ?: error("Unable to find who triggered the build")

internal class TriggeredBy(val name: String, val email: String)

internal fun isScheduled() = triggeredByName?.contains("Schedule") == true

private val triggeredByName by lazy {
  System.getProperty("teamcity.build.triggeredBy")
}