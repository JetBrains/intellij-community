// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.function.Consumer

internal fun report(
  root: File, devIcons: Int, icons: Int, skipped: Int,
  addedByDev: Collection<String>, removedByDev: Collection<String>,
  modifiedByDev: Collection<String>, addedByDesigners: Collection<String>,
  removedByDesigners: Collection<String>, modifiedByDesigners: Collection<String>,
  consistent: Collection<String>, errorHandler: Consumer<String>, doNotify: Boolean,
  doSyncIconsAndCreateReview: Boolean, doSyncDevIconsAndCreateReview: Boolean,
  devIconsVerifier: Runnable?
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
    if (!isUnderTeamCity()) {
      log("TeamCity url is unknown: unable to query last build status for sending notifications and assigning investigations")
    }
    else {
      val investigator = if (!success && !isInvestigationAssigned()) {
        assignInvestigation(root, doSyncIconsAndCreateReview, addedByDev, removedByDev, modifiedByDev)
      }
      else null
      sendNotification(success, investigator)
    }
    if (!success) errorHandler.accept(report)
  }
}

private val UPSOURCE_ICONS_PROJECT_ID = System.getProperty("intellij.icons.upsource.project.id")

private fun assignInvestigation(root: File,
                                doSyncIconsRepoAndCreateReview: Boolean,
                                addedByDev: Collection<String>,
                                removedByDev: Collection<String>,
                                modifiedByDev: Collection<String>): Investigator? =
  callSafely {
    var investigator = findInvestigator(root, addedByDev, removedByDev, modifiedByDev)
    if (doSyncIconsRepoAndCreateReview &&
        investigator?.commits?.isNotEmpty() == true &&
        DEFAULT_INVESTIGATOR.isNotBlank()) {
      val commitMessage = "Synchronization of changed icons from\n${investigator.commits
        .groupBy { it.repo }
        .map { "${getOriginUrl(it.key)}: ${it.value.map(CommitInfo::hash).joinToString()}" }
        .joinToString(System.lineSeparator())}"
      val review = pushAndCreateReview(commitMessage)
      investigator.assignedReview = review.id
      investigator = assignInvestigation(investigator)
      addReviewer(UPSOURCE_ICONS_PROJECT_ID, review, investigator.email)
    }
    else {
      investigator = assignInvestigation(investigator)
    }
    investigator
  }

private fun findInvestigator(root: File,
                             addedByDev: Collection<String>,
                             removedByDev: Collection<String>,
                             modifiedByDev: Collection<String>): Investigator? {
  val commits = (addedByDev.asSequence() + removedByDev.asSequence() + modifiedByDev.asSequence()).map {
    val path = File(root, it).absolutePath
    val commit = latestChangeCommit(path)
    if (commit != null) commit to it else null
  }.filterNotNull().toList()
  return commits
    .groupBy { it.first.committerEmail }
    .maxBy { it.value.size }
    ?.let { entry ->
      Investigator(email = entry.key,
                   commits = commits.map { it.first }.distinctBy { it.hash },
                   icons = commits.map { it.second })
    }
}

private fun pushAndCreateReview(message: String): Review {
  val branch = "icons-sync-${UUID.randomUUID()}"
  try {
    val commit = commitAndPush(iconsRepo, branch, message)
    return retry(
      doRetry = {
        if (it.message?.contains("Cannotresolverevision") == true) {
          log("Upsource hasn't updated branch list yet")
          true
        }
        else false
      }, action = {
      createReview(UPSOURCE_ICONS_PROJECT_ID, commit).also {
        log("Review successfully created: ${it.url}")
      }
    })
  }
  catch (e: Throwable) {
    deleteBranch(iconsRepo, branch)
    throw e
  }
}

private fun sendNotification(isSuccess: Boolean, investigator: Investigator?) {
  callSafely {
    if (isNotificationRequired(isSuccess)) {
      notifySlackChannel(isSuccess, investigator)
    }
  }
}

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")
private val INTELLIJ_ICONS_SYNC_RUN_CONF = System.getProperty("intellij.icons.sync.run.conf")

private fun notifySlackChannel(isSuccess: Boolean, investigator: Investigator?) {
  val investigation = when {
    investigator == null -> ""
    investigator.isAssigned -> "Investigation is assigned to ${investigator.email}\n"
    else -> "Unable to assign investigation to ${investigator.email}\n"
  }
  val hint = when {
    isSuccess -> ""
    investigator?.assignedReview != null -> "<${investigator.assignedReview}|Review and cherry-pick>\n"
    else -> "Use 'Icons processing/*$INTELLIJ_ICONS_SYNC_RUN_CONF*' IDEA Ultimate run configuration\n"
  }
  val reaction = if (isSuccess) ":white_check_mark:" else ":scream:"
  val buildServerUrlForReport = System.getProperty("intellij.icons.report.buildserver")
  val build = "<$buildServerUrlForReport/viewLog.html?buildId=$BUILD_ID&buildTypeId=$BUILD_CONF|See build log>"
  val text = "*${System.getProperty("teamcity.buildConfName")}* $reaction\n$investigation$hint$build"
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}
