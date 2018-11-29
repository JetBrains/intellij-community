// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

internal fun report(context: Context, skipped: Int): String {
  val (devIcons, icons) = context.devIcons.size to context.icons.size
  log("Skipped $skipped dirs")
  fun Collection<String>.logIcons(description: String) = "$size $description${if (size < 100) ": ${joinToString()}" else ""}"
  @Suppress("Duplicates")
  var report = when {
    context.iconsCommitHashesToSync.isNotEmpty() -> """
      |${context.iconsRepoName} commits ${context.iconsCommitHashesToSync.joinToString()} are synced into ${context.devRepoName}:
      | ${context.byDesigners.added.logIcons("added")}
      | ${context.byDesigners.removed.logIcons("removed")}
      | ${context.byDesigners.modified.logIcons("modified")}
    """.trimMargin()
    context.devIconsCommitHashesToSync.isNotEmpty() -> """
      |${context.devRepoName} commits ${context.devIconsCommitHashesToSync.joinToString()} are synced into ${context.iconsRepoName}:
      | ${context.byDev.added.logIcons("added")}
      | ${context.byDev.removed.logIcons("removed")}
      | ${context.byDev.modified.logIcons("modified")}
    """.trimMargin()
    else -> """
      |$devIcons icons are found in ${context.devRepoName}:
      | ${context.byDev.added.logIcons("added")}
      | ${context.byDev.removed.logIcons("removed")}
      | ${context.byDev.modified.logIcons("modified")}
      |$icons icons are found in ${context.iconsRepoName}:
      | ${context.byDesigners.added.logIcons("added")}
      | ${context.byDesigners.removed.logIcons("removed")}
      | ${context.byDesigners.modified.logIcons("modified")}
      |${context.consistent.size} consistent icons in both repos
    """.trimMargin()
  }
  if (context.createdReviews.isNotEmpty()) {
    report += "\nCreated reviews: ${context.createdReviews.joinToString { it.url }}"
  }
  return report
}

internal fun findCommitsToSync(context: Context) {
  // TODO: refactor it
  fun guessGitObject(repo: File, file: File) = GitObject(file.toRelativeString(repo), "-1", repo)
  if ((context.doSyncDevRepo || context.doSyncDevIconsAndCreateReview) && context.devSyncRequired()) {
    context.iconsCommitsToSync = findCommitsByRepo(context, UPSOURCE_DEV_PROJECT_ID, context.iconsRepoDir, context.byDesigners) {
      context.devIcons[it] ?: {
        val change = context.devRepoRoot.resolve(it)
        guessGitObject(changesToReposMap(change), change)
      }()
    }
  }
  if ((context.doSyncIconsRepo || context.doSyncIconsAndCreateReview) && context.iconsSyncRequired()) {
    context.devCommitsToSync = findCommitsByRepo(context, UPSOURCE_ICONS_PROJECT_ID, context.devRepoRoot, context.byDev) {
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
  val repos = context.iconsChanges().map {
    changesToReposMap(context.devRepoRoot.resolve(it))
  }.distinct()
  verifyDevIcons(context, repos)
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
        postVerificationResultToReview(review)
        review
      }
      catch (e: Exception) {
        closeReview(projectId, review)
        throw e
      }
    }
  }
}

private fun verifyDevIcons(context: Context, repos: Collection<File>) {
  callSafely {
    context.verifyDevIcons(repos)
  }
  repos.forEach { repo ->
    val status = gitStatus(repo)
    if (status.isNotEmpty()) {
      stageFiles(status, repo)
    }
  }
}

private fun postVerificationResultToReview(review: Review) {
  val runConfigurations = System.getProperty("sync.dev.icons.checks")?.splitNotBlank(";") ?: return
  postComment(UPSOURCE_DEV_PROJECT_ID, review,
              "Following configurations were run: ${runConfigurations.joinToString()}, see build ${thisBuildReportableLink()}")
}

private fun createReviewForIcons(context: Context, user: String, email: String): Collection<Review> {
  if (!context.doSyncIconsAndCreateReview || context.devCommitsToSync.isEmpty()) return emptyList()
  val repos = listOf(context.iconsRepo)
  return context.devCommitsToSync.values.flatten()
    .groupBy(CommitInfo::committerEmail)
    .entries.parallelStream()
    .map {
      val (committer, commits) = it
      withTmpBranch(repos) { branch ->
        commits.forEach { commit ->
          val change = context.byCommit[commit.hash] ?: error("Unable to find changes for commit ${commit.hash} by $committer")
          log("[$committer] syncing ${commit.hash} in ${context.iconsRepoName}")
          syncIconsRepo(context, change)
        }
        val commitsForReview = commitAndPush(branch, user, email, commits.groupBy(CommitInfo::repo).commitMessage(), repos)
        val review = createReview(UPSOURCE_ICONS_PROJECT_ID, branch, commitsForReview)
        addReviewer(UPSOURCE_ICONS_PROJECT_ID, review, committer)
        review
      }
    }.filter(Objects::nonNull).map { it as Review }.toList()
}

internal fun createReviews(context: Context) = callSafely {
  val (user, email) = System.getProperty("upsource.user.name") to System.getProperty("upsource.user.email")
  context.createdReviews = Stream.of(
    { createReviewForDev(context, user, email)?.let { listOf(it) } ?: emptyList() },
    { createReviewForIcons(context, user, email) }
  ).parallel().flatMap { it().stream() }
    .filter(Objects::nonNull)
    .map { it as Review }
    .toList()
}

internal fun assignInvestigation(context: Context): Investigator? =
  callSafely {
    val commits = if (context.iconsSyncRequired()) context.devCommitsToSync else context.iconsCommitsToSync
    if (commits.isEmpty()) {
      log("No commits, no investigation")
      return@callSafely null
    }
    val investigator = commits.flatMap { it.value }.maxBy(CommitInfo::timestamp)?.let {
      log("Assigning investigation to ${it.committerEmail} as author of latest change ${it.hash}")
      Investigator(it.committerEmail, commits)
    } ?: Investigator()
    assignInvestigation(investigator, context)
  }

private fun findCommitsByRepo(context: Context, projectId: String?, root: File, changes: Changes,
                              resolveGitObject: (String) -> GitObject
): Map<File, Collection<CommitInfo>> {
  var alreadyInReview = emptyList<String>()
  var commits = findCommits(context, root, changes)
  if (commits.isEmpty()) return emptyMap()
  val titles = if (!projectId.isNullOrEmpty()) getOpenIconsReviewTitles(projectId!!) else emptyList()
  val before = commits.size
  commits = commits.filterNot { entry ->
    val (commit, change) = entry
    val skip = titles.any {
      it.contains(commit.hash)
    }
    if (skip) alreadyInReview += change
    skip
  }
  log("$projectId: ${before - commits.size} commits already in review")
  alreadyInReview
    .map { resolveGitObject(it) }
    .groupBy({ it.repo }, { it.path })
    .forEach {
      val (repo, skipped) = it
      log("Already in review, skipping: $skipped")
      unStageFiles(skipped, repo)
    }
  log("$projectId: ${commits.size} commits found")
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

private fun findCommits(context: Context, root: File, changes: Changes) = changes.all()
  .mapNotNull { change ->
    val absoluteFile = root.resolve(change)
    val repo = changesToReposMap(absoluteFile)
    val commit = latestChangeCommit(absoluteFile.toRelativeString(repo), repo)
    if (commit != null) commit to change else null
  }.onEach {
    val commit = it.first.hash
    val change = it.second
    if (!context.byCommit.containsKey(commit)) context.byCommit[commit] = Changes(changes.includeRemoved)
    val commitChange = context.byCommit[commit]!!
    when {
      changes.added.contains(change) -> commitChange.added += change
      changes.modified.contains(change) -> commitChange.modified += change
      changes.removed.contains(change) -> commitChange.removed += change
    }
  }.groupBy({ it.first }, { it.second })

private fun commitAndPush(branch: String,
                          user: String,
                          email: String,
                          message: String,
                          repos: Collection<File>) = repos.parallelStream().map {
  withUser(it, user, email) {
    commitAndPush(it, branch, message)
  }
}.toList()

internal fun sendNotification(investigator: Investigator?, context: Context) {
  callSafely {
    if (isNotificationRequired(context)) {
      notifySlackChannel(investigator, context)
    }
  }
}

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")

internal fun Context.report(slack: Boolean = false): String {
  val iconsSync = if (iconsSyncRequired() && iconsReviews().isNotEmpty()) {
    "To sync $iconsRepoName see ${iconsReviews().joinToString {
      if (slack) slackLink(it.id, it.url) else it.url
    }}" + if (slack) "\n" else ""
  }
  else ""
  val devSync = if (devReviews().isNotEmpty()) {
    "To sync $devRepoName see ${devReviews().joinToString {
      if (slack) slackLink(it.id, it.url) else it.url
    }}" + if (slack) "\n" else ""
  }
  else ""
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