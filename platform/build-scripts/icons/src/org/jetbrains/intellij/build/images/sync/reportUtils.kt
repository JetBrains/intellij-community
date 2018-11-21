// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

internal fun report(context: Context, skipped: Int, consistent: Collection<String>) {
  val (devIcons, icons) = context.devIcons.size to context.icons.size
  if (isUnderTeamCity()) {
    findCommitsToSync(context)
    createReviews(context)
    val investigator = if (context.isFail() && context.assignInvestigation) {
      assignInvestigation(context)
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

private fun findCommitsToSync(context: Context) {
  // TODO: refactor it
  fun guessGitObject(repo: File, file: File) = GitObject(file.toRelativeString(repo), "-1", repo)
  if (context.devSyncRequired()) {
    context.iconsCommitsToSync = findCommitsByRepo(UPSOURCE_DEV_PROJECT_ID, context.iconsRepoDir, context.iconsChanges) {
      context.devIcons[it] ?: {
        val change = context.devRepoRoot.resolve(it)
        guessGitObject(changesToReposMap(change), change)
      }()
    }
  }
  if (context.iconsSyncRequired()) {
    context.devCommitsToSync = findCommitsByRepo(UPSOURCE_ICONS_PROJECT_ID, context.devRepoRoot, context.devChanges) {
      context.icons[it] ?: guessGitObject(context.iconsRepo, context.iconsRepoDir.resolve(it))
    }
  }
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

private fun createReviewForDev(context: Context, user: String, email: String): Review? {
  if (!context.doSyncDevIconsAndCreateReview || context.iconsCommitsToSync.isEmpty()) return null
  val repos = context.iconsChanges.map {
    changesToReposMap(context.devRepoRoot.resolve(it))
  }.distinct()
  val verificationPassed = verifyDevIcons(context, repos)
  return withTmpBranch(repos) { branch ->
    val commitsForReview = commitAndPush(branch, user, email, context.iconsCommitsToSync.commitMessage(), repos)
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

private fun createReviewForIcons(context: Context, user: String, email: String): Review? {
  if (!context.doSyncIconsAndCreateReview || context.devCommitsToSync.isEmpty()) return null
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

private fun createReviews(context: Context) = callSafely {
  val (user, email) = System.getProperty("upsource.user.name") to System.getProperty("upsource.user.email")
  context.createdReviews = Stream.of(
    { createReviewForDev(context, user, email) },
    { createReviewForIcons(context, user, email) }
  ).parallel().map { it() }
    .filter(Objects::nonNull)
    .map { it as Review }
    .toList()
}

private fun assignInvestigation(context: Context): Investigator? =
  callSafely {
    val commits = if (context.iconsSyncRequired()) context.devCommitsToSync else context.iconsCommitsToSync
    val investigator = commits.flatMap { it.value }.maxBy(CommitInfo::timestamp)?.let {
      Investigator(it.committerEmail, commits)
    } ?: Investigator()
    assignInvestigation(investigator, context)
  }

private fun findCommitsByRepo(projectId: String, root: File,
                              changes: Collection<String>,
                              resolveGitObject: (String) -> GitObject
): Map<File, Collection<CommitInfo>> {
  var alreadyInReview = emptyList<String>()
  var commits = findCommits(root, changes.asSequence())
  if (commits.isEmpty()) return emptyMap()
  val titles = getOpenIconsReviewTitles(projectId)
  var before = commits.size
  commits = commits.filterNot { entry ->
    val (commit, change) = entry
    val skip = titles.any {
      it.contains(commit.hash)
    }
    if (skip) alreadyInReview += change
    skip
  }
  log("$projectId: ${before - commits.size} commits already in review")
  val commitsToSync = System.getProperty("sync.icons.commits")
                        ?.takeIf { it.trim() != "*" }
                        ?.split(",", ";", " ")
                        ?.filter { it.isNotBlank() }
                        ?.mapTo(mutableSetOf(), String::trim) ?: emptySet<String>()
  if (commitsToSync.isNotEmpty()) {
    log("Commits to sync: $commitsToSync")
    before = commits.size
    commits = commits.filter {
      commitsToSync.contains(it.key.hash)
    }
    log("$projectId: skipped ${before - commits.size} commits")
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

@Volatile
private var changesToReposMap = emptyMap<File, File>()
private val changesToReposMapGuard = Any()
internal fun changesToReposMap(change: File): File {
  if (!changesToReposMap.containsKey(change)) synchronized(changesToReposMapGuard) {
    if (!changesToReposMap.containsKey(change)) {
      changesToReposMap += change to findGitRepoRoot(change, silent = true)
    }
  }
  return changesToReposMap[change]!!
}

private fun findCommits(root: File, changes: Sequence<String>) = changes.map { change ->
  val absoluteFile = root.resolve(change)
  val repo = changesToReposMap(absoluteFile)
  val commit = latestChangeCommit(absoluteFile.toRelativeString(repo), repo)
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