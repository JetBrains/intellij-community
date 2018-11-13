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

private fun withTmpBranch(repos: Collection<File>, action: (String) -> Review?): Review? {
  val branch = "icons-sync/${UUID.randomUUID()}"
  return try {
    action(branch)
  }
  catch (e: Throwable) {
    repos.forEach {
      deleteBranch(it, branch)
    }
    throw e
  }
}

private fun createReviewForDev(root: File, context: Context, user: String, email: String): Review? {
  if (!context.doSyncDevIconsAndCreateReview) return null
  val changes = context.addedByDesigners + context.modifiedByDesigners + if (context.doSyncRemovedIconsInDev) {
    context.removedByDesigners
  }
  else emptyList()
  if (changes.isEmpty()) return null
  val repos = changes.asSequence()
    .map { File(root, it).absolutePath }
    .map { findGitRepoRoot(it, silent = true) }
    .distinct()
    .toList()
  val commits = findCommitsByRepo(File(context.iconsRepoDir), changes.asSequence())
  if (commits.isEmpty()) return null
  val verificationPassed = try {
    context.verifyDevIcons()
    repos.forEach { repo ->
      val status = gitStatus(repo)
      if (status.isNotEmpty()) {
        addChangesToGit(status, repo)
      }
    }
    true
  }
  catch (e: Exception) {
    e.printStackTrace()
    false
  }
  return withTmpBranch(repos) { branch ->
    val commitsForReview = commitAndPush(branch, user, email, commits.commitMessage(), repos)
    if (UPSOURCE_DEV_PROJECT_ID.isNullOrEmpty()) {
      log("WARNING: unable to create Upsource review for ${context.devRepoName}, just plain old branch review")
      PlainOldReview(branch, UPSOURCE_DEV_PROJECT_ID)
    }
    else {
      val review = createReview(UPSOURCE_DEV_PROJECT_ID, branch, commits.commitMessage(), commitsForReview)
      addReviewer(UPSOURCE_DEV_PROJECT_ID, review, triggeredBy() ?: DEFAULT_INVESTIGATOR)
      postVerificationResultToReview(verificationPassed, review)
      review
    }
  }
}

private fun postVerificationResultToReview(success: Boolean, review: Review) {
  val runConfigurations = System.getProperty("sync.dev.icons.checks")?.splitNotBlank(";") ?: return
  val comment = if (success) "Following checks were successful:" else "Some of the following checks failed:"
  postComment(UPSOURCE_DEV_PROJECT_ID, review, "$comment ${runConfigurations.joinToString()}, see build log ${thisBuildReportableLink()}")
}

private fun createReviewForIcons(root: File, context: Context, user: String, email: String): Review? {
  if (!context.doSyncIconsAndCreateReview) return null
  val changes = context.addedByDev + context.removedByDev + context.modifiedByDev
  if (changes.isEmpty()) return null
  val commits = findCommitsByRepo(root, changes.asSequence())
  if (commits.isEmpty()) return null
  val repos = listOf(context.iconsRepo)
  return withTmpBranch(repos) { branch ->
    val commitsForReview = commitAndPush(branch, user, email, commits.commitMessage(), repos)
    val review = createReview(UPSOURCE_ICONS_PROJECT_ID, branch, commits.commitMessage(), commitsForReview)
    commits.values.parallelStream()
      .flatMap { it.stream() }
      .map { it.committerEmail }
      .distinct()
      .forEach { addReviewer(UPSOURCE_ICONS_PROJECT_ID, review, it) }
    review
  }
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

private fun commitAndPush(branch: String, user: String, email: String, message: String, repos: Collection<File>) = repos.map {
  initGit(it, user, email)
  commitAndPush(it, branch, message)
}

private fun createReview(project: String, branch: String,
                         message: String, commits: Collection<String>) = createReview(project, branch, commits).also {
  postComment(project, it, message)
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

internal fun Context.report(slack: Boolean = false): String {
  val iconsSync = when {
    !iconsSyncRequired() -> ""
    iconsReview() != null -> iconsReview()!!.let {
      val link = if (slack) slackLink(it.id, it.url) else it.url
      "To sync $iconsRepoName see $link\n"
    }
    else -> "Use 'Icons processing/*$ICONS_REPO_SYNC_RUN_CONF*' IDEA Ultimate run configuration\n"
  }
  val devSync = when {
    !devSyncRequired() -> ""
    devReview() != null -> devReview()!!.let {
      val link = if (slack) slackLink(it.id, it.url) else it.url
      "To sync $devRepoName see $link\n"
    }
    DEV_REPO_SYNC_BUILD_CONF != null -> {
      val link = buildConfReportableLink(DEV_REPO_SYNC_BUILD_CONF).let {
        if (slack) slackLink("this build", it) else it
      }
      "To sync $devRepoName run $link"
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
  val build = slackLink("See build log", thisBuildReportableLink())
  val text = "*${context.devRepoName}* $reaction\n" + investigation + context.report(slack = true) + build
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}

private fun slackLink(name: String, link: String) = if (link == name) link else "<$link|$name>"