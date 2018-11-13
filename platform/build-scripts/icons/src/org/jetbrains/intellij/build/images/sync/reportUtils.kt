// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

internal fun report(context: Context, root: File, devIcons: Int, icons: Int, skipped: Int, consistent: Collection<String>) {
  if (isUnderTeamCity()) {
    val isInvestigationAssigned = isInvestigationAssigned()
    if (isInvestigationAssigned) {
      log("Skipping review creation since investigation is assigned")
    }
    else {
      createReviews(root, context)
    }
    val investigator = if (context.isFail() &&
                           context.assignInvestigation &&
                           !isInvestigationAssigned) {
      assignInvestigation(root, context)
    }
    else null
    if (context.notifySlack) sendNotification(investigator, context)
  }
  log("Skipped $skipped dirs")
  fun Collection<String>.logIcons(description: String) = "$size $description${if (size < 100) ": ${joinToString()}" else ""}"
  val report = """
    |$devIcons icons are found in dev repo:
    | ${context.addedByDev.logIcons("added")}
    | ${context.removedByDev.logIcons("removed")}
    | ${context.modifiedByDev.logIcons("modified")}
    |$icons icons are found in icons repo:
    | ${context.addedByDesigners.logIcons("added")}
    | ${context.removedByDesigners.logIcons("removed")}
    | ${context.modifiedByDesigners.logIcons("modified")}
    |${consistent.size} consistent icons in both repos
    |${if (context.createdReviews.isNotEmpty()) "Created reviews: ${context.createdReviews.map(Review::url)}" else ""}
  """.trimMargin()
  log(report)
  if (isUnderTeamCity() && context.isFail()) context.doFail(report)
}

internal fun Map<File, Collection<CommitInfo>>.description() = entries.joinToString(System.lineSeparator()) { entry ->
  "${getOriginUrl(entry.key)}: ${entry.value.joinToString { it.hash }}"
}

private fun Map<File, Collection<CommitInfo>>.commitMessage() = "Synchronization of changed icons from ${description()}"

private fun createReviewForDev(root: File, context: Context, user: String, email: String): Review? {
  if (!context.doSyncDevIconsAndCreateReview) return null
  val changes = context.addedByDesigners + context.removedByDesigners + context.modifiedByDesigners
  if (changes.isNotEmpty()) {
    val repos = changes.asSequence()
      .map { File(root, it).absolutePath }
      .map { findGitRepoRoot(it, silent = true) }
      .distinct()
      .toList()
    val commits = findCommitsByRepo(context.iconsRepo, changes.asSequence())
    if (commits.isNotEmpty()) {
      val verificationPassed = try {
        context.verifyDevIcons()
        repos.forEach { repo ->
          gitStatus(repo)
            .takeIf { it.isNotEmpty() }
            ?.also { addChangesToGit(it, repo) }
        }
        true
      }
      catch (e: Exception) {
        e.printStackTrace()
        false
      }
      val review = pushAndCreateReview(UPSOURCE_DEV_PROJECT_ID, user, email, commits.commitMessage(), repos)
      addReviewer(UPSOURCE_DEV_PROJECT_ID, review, triggeredBy() ?: DEFAULT_INVESTIGATOR)
      postVerificationResultToReview(verificationPassed, review)
      return review
    }
  }
  return null
}

private fun postVerificationResultToReview(success: Boolean, review: Review) {
  val runConfigurations = System.getProperty("sync.dev.icons.checks")?.splitNotBlank(";") ?: return
  val comment = if (success) "Following checks were successful:" else "Some of the following checks failed:"
  postComment(UPSOURCE_DEV_PROJECT_ID, review, "$comment ${runConfigurations.joinToString()}, see build log ${thisBuildReportableLink()}")
}

private fun createReviewForIcons(root: File, context: Context, user: String, email: String): Review? {
  if (!context.doSyncIconsAndCreateReview) return null
  val changes = context.addedByDev + context.removedByDev + context.modifiedByDev
  if (changes.isNotEmpty()) {
    val commits = findCommitsByRepo(root, changes.asSequence())
    if (commits.isNotEmpty()) {
      val review = pushAndCreateReview(UPSOURCE_ICONS_PROJECT_ID, user, email,
                                       commits.commitMessage(),
                                       listOf(context.iconsRepo))
      commits.values.parallelStream()
        .flatMap { it.stream() }
        .map { it.committerEmail }
        .distinct()
        .forEach { addReviewer(UPSOURCE_ICONS_PROJECT_ID, review, it) }
      return review
    }
  }
  return null
}

private fun createReviews(root: File, context: Context) = callSafely {
  val (user, email) = System.getProperty("upsource.user.name") to System.getProperty("upsource.user.email")
  context.createdReviews = Stream.of(
    { createReviewForDev(root, context, user, email) },
    { createReviewForIcons(root, context, user, email) }
  ).parallel().map { it() }
    .filter(Objects::nonNull)
    .map { it as Review }
    .toList()
}

private fun assignInvestigation(root: File, context: Context): Investigator? =
  callSafely {
    assignInvestigation(findInvestigator(root, context.addedByDev.asSequence() +
                                               context.removedByDev.asSequence() +
                                               context.modifiedByDev.asSequence()), context)
  }

private fun findInvestigator(root: File, changes: Sequence<String>): Investigator {
  val commits = findCommits(root, changes).toList()
  return commits.groupBy { it.committerEmail }
           .maxBy { it.value.size }
           ?.let { entry ->
             Investigator(entry.key, commits.asSequence()
               .distinctBy { it.hash }
               .groupBy { it.repo })
           } ?: Investigator()
}

private fun findCommitsByRepo(root: File, changes: Sequence<String>) =
  findCommits(root, changes).distinctBy { it.hash }.groupBy { it.repo }

private fun findCommits(root: File, changes: Sequence<String>) = changes
  .map { File(root, it).absolutePath }
  .map { latestChangeCommit(it) }
  .filterNotNull()

private fun pushAndCreateReview(project: String, user: String, email: String, message: String, repos: Collection<File>): Review {
  val branch = "icons-sync/${UUID.randomUUID()}"
  try {
    val commits = repos.map {
      initGit(it, user, email)
      commitAndPush(it, branch, message)
    }
    val review = createReview(project, branch, commits)
    postComment(project, review, message)
    return review
  }
  catch (e: Throwable) {
    repos.forEach {
      deleteBranch(it, branch)
    }
    throw e
  }
}

private fun sendNotification(investigator: Investigator?, context: Context) {
  callSafely {
    if (isNotificationRequired(context)) {
      notifySlackChannel(investigator, context)
    }
  }
}

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")
private val ICONS_REPO_SYNC_RUN_CONF = System.getProperty("intellij.icons.sync.run.conf")
private val DEV_REPO_SYNC_BUILD_CONF = System.getProperty("intellij.icons.dev.sync.build.conf")

internal fun Context.report() : String {
  val iconsSync = when {
    !iconsSyncRequired() -> ""
    iconsReview() != null -> iconsReview()!!.let {
      "To sync ${iconsRepoName} see <${it.url}|${it.id}>\n"
    }
    else -> "Use 'Icons processing/*$ICONS_REPO_SYNC_RUN_CONF*' IDEA Ultimate run configuration\n"
  }
  val devSync = when {
    !devSyncRequired() -> ""
    devReview() != null -> devReview()!!.let {
      "To sync $devRepoName see <${it.url}|${it.id}>\n"
    }
    DEV_REPO_SYNC_BUILD_CONF != null -> {
      "To sync $devRepoName run <${buildConfReportableLink(DEV_REPO_SYNC_BUILD_CONF)}|this build>"
    }
    else -> ""
  }
  return iconsSync + devSync
}

private fun notifySlackChannel(investigator: Investigator?, context: Context) {
  val investigation = when {
    investigator == null -> ""
    investigator.isAssigned -> "Investigation is assigned to ${investigator.email}\n"
    else -> "Unable to assign investigation to ${investigator.email}\n"
  }

  val reaction = if (context.isFail()) ":scream:" else ":white_check_mark:"
  val build = "<${thisBuildReportableLink()}|See build log>"
  val text = "*${context.iconsRepoName}* $reaction\n" + investigation + context.report() + build
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}
