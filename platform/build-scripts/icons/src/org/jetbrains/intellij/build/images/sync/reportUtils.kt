// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

internal fun report(context: Context, devRepoRoot: File, skipped: Int, consistent: Collection<String>) {
  val (devIcons, icons) = context.devIcons.size to context.icons.size
  if (isUnderTeamCity()) {
    createReviews(devRepoRoot, context)
    val investigator = if (context.isFail() && context.assignInvestigation) {
      assignInvestigation(devRepoRoot, context)
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

internal fun Map<File, Collection<CommitInfo>>.description() = entries.joinToString { entry ->
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

private fun createReviewForDev(devRepoRoot: File, context: Context, user: String, email: String): Review? {
  if (!context.doSyncDevIconsAndCreateReview) return null
  val changes = context.addedByDesigners + context.modifiedByDesigners + if (context.doSyncRemovedIconsInDev) {
    context.removedByDesigners
  }
  else emptyList()
  if (changes.isEmpty()) return null
  val changesMap = changes.associate {
    val path = devRepoRoot.resolve(it).absolutePath
    it to findGitRepoRoot(path, silent = true)
  }
  val commits = findCommitsByRepo(UPSOURCE_DEV_PROJECT_ID, File(context.iconsRepoDir), changes) {
    context.devIcons[it] ?: guessGitObject(changesMap[it]!!, devRepoRoot.resolve(it))
  }
  if (commits.isEmpty()) return null
  val repos = changesMap.values.distinct()
  val verificationPassed = verifyDevIcons(context, repos)
  return withTmpBranch(repos) { branch ->
    val commitsForReview = commitAndPush(branch, user, email, commits.commitMessage(), repos)
    val projectId = UPSOURCE_DEV_PROJECT_ID
    if (projectId.isNullOrEmpty()) {
      log("WARNING: unable to create Upsource review for ${context.devRepoName}, just plain old branch review")
      PlainOldReview(branch, projectId)
    }
    else {
      val review = createReview(projectId, branch, commitsForReview)
      try {
        addReviewer(projectId, review, triggeredBy() ?: DEFAULT_INVESTIGATOR)
        postVerificationResultToReview(verificationPassed, review)
        review
      }
      catch (e: Exception) {
        closeReview(projectId, review)
        throw e
      }
    }
  }
}

private fun verifyDevIcons(context: Context, repos: Collection<File>) = try {
  context.verifyDevIcons()
  repos.forEach { repo ->
    val status = gitStatus(repo)
    if (status.isNotEmpty()) {
      stageFiles(status, repo)
    }
  }
  true
}
catch (e: Exception) {
  e.printStackTrace()
  false
}

private fun postVerificationResultToReview(success: Boolean, review: Review) {
  val runConfigurations = System.getProperty("sync.dev.icons.checks")?.splitNotBlank(";") ?: return
  val comment = if (success) "Following checks were successful:" else "Some of the following checks failed:"
  postComment(UPSOURCE_DEV_PROJECT_ID, review, "$comment ${runConfigurations.joinToString()}, see build log ${thisBuildReportableLink()}")
}

// TODO: refactor it
private fun guessGitObject(repo: File, file: File) = GitObject(file.toRelativeString(repo), "-1", repo)

private fun createReviewForIcons(devRepoRoot: File, context: Context, user: String, email: String): Review? {
  if (!context.doSyncIconsAndCreateReview) return null
  val changes = context.addedByDev + context.removedByDev + context.modifiedByDev
  if (changes.isEmpty()) return null
  context.devCommitsToSync = findCommitsByRepo(UPSOURCE_ICONS_PROJECT_ID, devRepoRoot, changes) {
    context.icons[it] ?: guessGitObject(context.iconsRepo, File(context.iconsRepoDir).resolve(it))
  }
  if (context.devCommitsToSync.isEmpty()) return null
  val repos = listOf(context.iconsRepo)
  return withTmpBranch(repos) { branch ->
    val commitsForReview = commitAndPush(branch, user, email, context.devCommitsToSync.commitMessage(), repos)
    val review = createReview(UPSOURCE_ICONS_PROJECT_ID, branch, commitsForReview)
    context.devCommitsToSync.values.parallelStream()
      .flatMap { it.stream() }
      .map { it.committerEmail }
      .distinct()
      .forEach { addReviewer(UPSOURCE_ICONS_PROJECT_ID, review, it) }
    review
  }
}

private fun createReviews(devRepoRoot: File, context: Context) = callSafely {
  val (user, email) = System.getProperty("upsource.user.name") to System.getProperty("upsource.user.email")
  context.createdReviews = Stream.of(
    { createReviewForDev(devRepoRoot, context, user, email) },
    { createReviewForIcons(devRepoRoot, context, user, email) }
  ).parallel().map { it() }
    .filter(Objects::nonNull)
    .map { it as Review }
    .toList()
}

private fun assignInvestigation(devRepoRoot: File, context: Context): Investigator? =
  callSafely {
    assignInvestigation(findInvestigator(devRepoRoot, context), context)
  }

private fun findInvestigator(devRepoRoot: File, context: Context): Investigator {
  val changes = context.addedByDev.asSequence() +
                context.removedByDev.asSequence() +
                context.modifiedByDev.asSequence()
  val commits = findCommits(devRepoRoot, changes).keys
  val commitsToInvestigate = if (context.devCommitsToSync.isNotEmpty())
    context.devCommitsToSync
  else commits.groupBy(CommitInfo::repo)
  return commits.maxBy(CommitInfo::timestamp)?.let {
    Investigator(it.committerEmail, commitsToInvestigate)
  } ?: Investigator()
}

private fun findCommitsByRepo(projectId: String, root: File,
                              changes: Collection<String>,
                              resolveGitObject: (String) -> GitObject
): Map<File, Collection<CommitInfo>> {
  var alreadyInReview = emptyList<String>()
  var commits = findCommits(root, changes.asSequence())
  if (commits.isEmpty()) return emptyMap()
  val titles = getOpenIconsReviewTitles(projectId)
  commits = commits.filterNot { entry ->
    val (commit, change) = entry
    val skip = titles.any {
      it.contains(commit.hash)
    }
    if (skip) alreadyInReview += change
    skip
  }
  alreadyInReview
    .map { resolveGitObject(it) }
    .groupBy({ it.repo }, { it.path })
    .forEach {
      val (repo, skipped) = it
      log("Already in review, skipping: $skipped")
      unstageFiles(skipped, repo)
    }
  return commits.map { it.key }.groupBy(CommitInfo::repo)
}

private fun findCommits(root: File, changes: Sequence<String>) = changes.map { change ->
  val commit = latestChangeCommit(File(root, change).absolutePath)
  if (commit != null) commit to change else null
}.filterNotNull().groupBy({ it.first }, { it.second })

private fun commitAndPush(branch: String, user: String, email: String, message: String, repos: Collection<File>) = repos.map {
  initGit(it, user, email)
  commitAndPush(it, branch, message)
}

private fun sendNotification(investigator: Investigator?, context: Context) {
  callSafely {
    if (isNotificationRequired(context)) {
      notifySlackChannel(investigator, context)
    }
  }
}

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")

internal fun Context.report(slack: Boolean = false): String {
  val iconsSync = when {
    iconsSyncRequired() && iconsReview() != null -> iconsReview()!!.let {
      val link = if (slack) slackLink(it.id, it.url) else it.url
      "To sync $iconsRepoName see $link\n"
    }
    else -> ""
  }
  val devSync = devReview()?.let {
    val link = if (slack) slackLink(it.id, it.url) else it.url
    "To sync $devRepoName see $link\n"
  } ?: ""
  return iconsSync + devSync
}

private fun notifySlackChannel(investigator: Investigator?, context: Context) {
  val investigation = when {
    investigator == null -> ""
    investigator.isAssigned -> "Investigation is assigned to ${investigator.email}\n"
    else -> "Unable to assign investigation to ${investigator.email}\n"
  }
  val reaction = if (context.isFail()) ":scream:" else ":white_check_mark:"
  val build = "See " + slackLink("build log", thisBuildReportableLink())
  val text = "*${context.devRepoName}* $reaction\n" + investigation + context.report(slack = true) + build
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}

private fun slackLink(name: String, link: String) = if (link == name) link else "<$link|$name>"