// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.util.function.Consumer

internal fun report(
  devIcons: Int, icons: Int, skipped: Int,
  addedByDev: Collection<String>, removedByDev: Collection<String>,
  modifiedByDev: Collection<String>, addedByDesigners: Collection<String>,
  removedByDesigners: Collection<String>, modifiedByDesigners: Collection<String>,
  consistent: Collection<String>, errorHandler: Consumer<String>, doSync: Boolean
) {
  log("Skipped $skipped dirs")
  fun Collection<String>.logIcons() = if (size < 100) joinToString() else size.toString()
  log("""
    |dev repo:
    | added: ${addedByDev.logIcons()}
    | removed: ${removedByDev.logIcons()}
    | modified: ${modifiedByDev.logIcons()}
    |icons repo:
    | added: ${addedByDesigners.logIcons()}
    | removed: ${removedByDesigners.logIcons()}
    | modified: ${modifiedByDesigners.logIcons()}
  """.trimMargin())
  val report = """
    |$devIcons icons are found in dev repo:
    | ${addedByDev.size} added
    | ${removedByDev.size} removed
    | ${modifiedByDev.size} modified
    |$icons icons are found in icons repo:
    | ${addedByDesigners.size} added
    | ${removedByDesigners.size} removed
    | ${modifiedByDesigners.size} modified
    |${consistent.size} consistent icons in both repos
  """.trimMargin()
  log(report)
  if (!doSync) {
    val success = addedByDev.isEmpty() && removedByDev.isEmpty() && modifiedByDev.isEmpty()
    sendNotification(success, report)
    if (!success) errorHandler.accept(report)
  }
}

private fun sendNotification(isSuccess: Boolean, report: String) {
  if (BUILD_SERVER == null) {
    log("TeamCity url is unknown: unable to query last build status and send Slack channel notification")
  }
  else {
    callSafely {
      if (teamCityBuildStatusChanged(isSuccess)) {
        notifySlackChannel(isSuccess, report)
      }
    }
  }
}

private val BUILD_SERVER = System.getProperty("teamcity.serverUrl")
private val BUILD_CONF = System.getProperty("teamcity.buildType.id")

private fun teamCityBuildStatusChanged(isSuccess: Boolean) =
  HttpClients.createDefault().use {
    val get = HttpGet("$BUILD_SERVER/guestAuth/app/rest/builds?locator=buildType:$BUILD_CONF,count:1")
    val response = EntityUtils.toString(it.execute(get).entity, Charsets.UTF_8)
    isSuccess && response.contains("status=\"FAILURE\"") ||
    !isSuccess && response.contains("status=\"SUCCESS\"")
  }

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")
private val BUILD_ID = System.getProperty("teamcity.build.id")

private fun notifySlackChannel(isSuccess: Boolean, report: String) {
  HttpClients.createDefault().use {
    val text = (if (isSuccess) ":white_check_mark:" else ":scream:") + "\n$report\n" +
               (if (!isSuccess) "Use 'Sync icons in IntelliJIcons' IDEA Ultimate run configuration\n" else "") +
               "<$BUILD_SERVER/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF|See build log>"
    val post = HttpPost(CHANNEL_WEB_HOOK)
    post.entity = StringEntity("""{ "text": "$text" }""", Charsets.UTF_8)
    val response = EntityUtils.toString(it.execute(post).entity, Charsets.UTF_8)
    if (response != "ok") throw IllegalStateException("$CHANNEL_WEB_HOOK responded with $response")
  }
}