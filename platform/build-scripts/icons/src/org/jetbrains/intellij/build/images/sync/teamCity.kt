// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request

internal fun isUnderTeamCity() = BUILD_SERVER != null
private val BUILD_SERVER = System.getProperty("teamcity.serverUrl")
private val BUILD_CONF = System.getProperty("teamcity.buildType.id")
private val BUILD_ID = System.getProperty("teamcity.build.id")

private fun teamCityGet(path: String) = loadUrl("$BUILD_SERVER/httpAuth/app/rest/$path") {
  teamCityAuth()
}

private val XML: MediaType by lazy { "application/xml".toMediaType() }

private fun teamCityPost(path: String, body: String): String {
  return post("$BUILD_SERVER/httpAuth/app/rest/$path", body, XML) {
    teamCityAuth()
    addHeader("Content-Type", "application/xml")
  }
}

private fun Request.Builder.teamCityAuth() {
  header("Authorization", Credentials.basic(System.getProperty("pin.builds.user.name"), System.getProperty("pin.builds.user.password")))
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

private fun isInvestigationAssigned(): Boolean {
  val investigation = investigation
  return investigation.contains("assignee") && !investigation.contains("GIVEN_UP")
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

internal fun thisBuildReportableLink(): String {
  return "${System.getProperty("intellij.icons.report.buildserver")}/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF"
}

internal fun triggeredBy(): Committer {
  return System.getProperty("teamcity.build.triggeredBy.username")
           ?.takeIf(String::isNotBlank)
           ?.takeIf { it != "null" && it != "n/a" }
           ?.let { teamCityGet("users/username:$it/email") }
           ?.removeSuffix(System.lineSeparator())
           ?.takeIf { triggeredByName != null }
           ?.let { email -> Committer(triggeredByName, email) }
         ?: Committer("IconSyncRobot", "icon-sync-robot-no-reply@jetbrains.com")
}

internal fun isScheduled() = triggeredByName?.contains("Schedule") == true

private val triggeredByName by lazy {
  System.getProperty("teamcity.build.triggeredBy")
}

internal fun triggerBuild(buildId: String, branch: String): String {
  return teamCityPost("buildQueue", """<build branchName="$branch"><buildType id="$buildId"/></build>""")
}