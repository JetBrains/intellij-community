// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

internal fun isUnderTeamCity() = BUILD_SERVER != null
private val BUILD_SERVER = System.getProperty("teamcity.serverUrl")
internal val BUILD_CONF = System.getProperty("teamcity.buildType.id")
internal val BUILD_ID = System.getProperty("teamcity.build.id")

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

internal fun isNotificationRequired(isSuccess: Boolean): Boolean {
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


internal val DEFAULT_INVESTIGATOR by lazy {
  System.getProperty("intellij.icons.sync.default.investigator")?.takeIf { it.isNotBlank() } ?: error("Specify default investigator")
}

internal class Investigator(val email: String = DEFAULT_INVESTIGATOR,
                            val commits: Collection<CommitInfo> = emptyList(),
                            val icons: Collection<String> = emptyList(),
                            var isAssigned: Boolean = false,
                            var assignedReview: String? = null)

internal fun isInvestigationAssigned() = teamCityGet("investigations?locator=buildType:$BUILD_CONF").contains("assignee")

internal fun assignInvestigation(input: Investigator?): Investigator {
  var investigator = input
  if (investigator != null) assignInvestigation(investigator)
  when {
    investigator != null && !investigator.isAssigned -> {
      investigator = Investigator(DEFAULT_INVESTIGATOR,
                                  investigator.commits, investigator.icons,
                                  assignedReview = investigator.assignedReview)
      assignInvestigation(investigator)
    }
    investigator == null -> {
      log("Unable to determine committer email for investigation assignment")
      investigator = Investigator()
      assignInvestigation(investigator)
    }
  }
  return investigator!!
}

private fun assignInvestigation(investigator: Investigator) {
  try {
    val id = teamCityGet("users/email:${investigator.email}/id")
    val text = investigator.run {
      (if (commits.isNotEmpty()) "commits ${commits.map { it.hash }.joinToString()}\n" else "") +
      (if (icons.isNotEmpty()) "icons ${icons.joinToString()}\n" else "") +
      (if (assignedReview != null) "review and cherry-pick $assignedReview\n" else "")
    } + "https://confluence.jetbrains.com/display/IDEA/Working+with+icons+in+IntelliJ+Platform"
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