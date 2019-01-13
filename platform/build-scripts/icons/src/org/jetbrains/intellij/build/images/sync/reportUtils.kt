// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.stream.Collectors
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
  if (context.doSyncDevRepo && context.devSyncRequired()) {
    context.iconsCommitsToSync = findCommitsByRepo(context, UPSOURCE_DEV_PROJECT_ID, context.iconsRepoDir, context.byDesigners) {
      context.devIcons[it] ?: {
        val change = context.devRepoRoot.resolve(it)
        guessGitObject(changesToReposMap(change), change)
      }()
    }
  }
  if (context.doSyncIconsRepo && context.iconsSyncRequired()) {
    context.devCommitsToSync = findCommitsByRepo(context, UPSOURCE_ICONS_PROJECT_ID, context.devRepoRoot, context.byDev) {
      context.icons[it] ?: guessGitObject(context.iconsRepo, context.iconsRepoDir.resolve(it))
    }
  }
}

private fun Map<File, Collection<CommitInfo>>.commitMessage() = "Synchronization of changed icons from ${entries.joinToString { entry ->
  "${getOriginUrl(entry.key)}: ${entry.value.joinToString { it.hash }}"
}}"

private fun withTmpBranch(repos: Collection<File>, master: String, action: (String) -> Review?): Review? {
  val branch = "icons-sync/${UUID.randomUUID()}"
  return try {
    action(branch)
  }
  catch (e: Throwable) {
    repos.parallelStream().forEach {
      deleteBranch(it, branch)
    }
    throw e
  }
  finally {
    repos.parallelStream().forEach {
      callSafely {
        checkout(it, master)
      }
    }
  }
}

private fun createReviewForDev(context: Context, user: String, email: String): Review? {
  if (context.iconsCommitsToSync.isEmpty()) return null
  val repos = context.iconsChanges().map {
    changesToReposMap(context.devRepoRoot.resolve(it))
  }.distinct()
  verifyDevIcons(context, repos)
  if (repos.all { gitStage(it).isEmpty() }) {
    log("Nothing to commit")
    context.byDesigners.clear()
    return null
  }
  val master = repos.parallelStream().map(::head).collect(Collectors.toSet()).single()
  return withTmpBranch(repos, master) { branch ->
    val commitsForReview = commitAndPush(branch, user, email, context.iconsCommitsToSync.commitMessage(), repos)
    val projectId = UPSOURCE_DEV_PROJECT_ID
    if (projectId.isNullOrEmpty()) {
      log("WARNING: unable to create Upsource review for ${context.devRepoName}, just plain old branch review")
      PlainOldReview(branch, projectId)
    }
    else {
      val review = createReview(projectId, branch, master, commitsForReview)
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
      log("Staging ${status.joinToString("," + System.lineSeparator()) {
        repo.resolve(it).toString()
      }}")
      status.forEach {
        stageFiles(listOf(it), repo)
      }
      log("Staged: " + gitStage(repo))
    }
  }
}

private fun postVerificationResultToReview(review: Review) {
  val runConfigurations = System.getProperty("sync.dev.icons.checks")?.splitNotBlank(";") ?: return
  postComment(UPSOURCE_DEV_PROJECT_ID, review,
              "Following configurations were run: ${runConfigurations.joinToString()}, see build ${thisBuildReportableLink()}")
}

private fun createReviewForIcons(context: Context, user: String, email: String): Collection<Review> {
  if (context.devCommitsToSync.isEmpty()) return emptyList()
  val repos = listOf(context.iconsRepo)
  val master = head(context.iconsRepo)
  return context.devCommitsToSync.values.flatten()
    .groupBy(CommitInfo::committerEmail)
    .entries.map {
    val (committer, commits) = it
    withTmpBranch(repos, master) { branch ->
      commits.forEach { commit ->
        val change = context.byCommit[commit.hash] ?: error("Unable to find changes for commit ${commit.hash} by $committer")
        log("[$committer] syncing ${commit.hash} in ${context.iconsRepoName}")
        syncIconsRepo(context, change)
      }
      if (gitStage(context.iconsRepo).isEmpty()) {
        log("Nothing to commit")
        context.byDev.clear()
        null
      }
      else {
        val commitsForReview = commitAndPush(branch, user, email, commits.groupBy(CommitInfo::repo).commitMessage(), repos)
        val review = createReview(UPSOURCE_ICONS_PROJECT_ID, branch, master, commitsForReview)
        addReviewer(UPSOURCE_ICONS_PROJECT_ID, review, committer)
        review
      }
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
    var (investigator, commits) = if (context.iconsSyncRequired()) {
      context.devCommitsToSync.flatMap { it.value }.maxBy(CommitInfo::timestamp)?.let { latest ->
        log("Assigning investigation to ${latest.committerEmail} as author of latest change ${latest.hash}")
        latest.committerEmail
      } to context.devCommitsToSync
    }
    else triggeredBy() to context.iconsCommitsToSync
    var reviews = emptyList<String>()
    if (commits.isEmpty()) {
      if (context.commitsAlreadyInReview.isEmpty()) {
        log("No commits, no investigation")
        return@callSafely null
      }
      context.commitsAlreadyInReview.keys.maxBy(CommitInfo::timestamp)?.let { latest ->
        val review = context.commitsAlreadyInReview[latest]!!
        log("Assigning investigation to ${latest.committerEmail} as author of latest change ${latest.hash} under review $review")
        investigator = latest.committerEmail
        commits = context.commitsAlreadyInReview.keys
          .filter { context.commitsAlreadyInReview[it] == review }
          .groupBy(CommitInfo::repo)
        reviews += review
      }
    }
    if (context.iconsSyncRequired() && context.iconsReviews().isNotEmpty()) {
      reviews += context.iconsReviews().map(Review::url)
    }
    if (context.devReviews().isNotEmpty()) {
      reviews += context.devReviews().map(Review::url)
    }
    assignInvestigation(Investigator(investigator ?: DEFAULT_INVESTIGATOR, commits), context, reviews)
  }

private fun findCommitsByRepo(context: Context, projectId: String?, root: File, changes: Changes,
                              resolveGitObject: (String) -> GitObject
): Map<File, Collection<CommitInfo>> {
  var changesAlreadyInReview = emptyList<String>()
  var commits = findCommits(context, root, changes)
  if (commits.isEmpty()) return emptyMap()
  val reviewTitles = if (!projectId.isNullOrEmpty()) {
    getOpenIconsReviewTitles(projectId)
  }
  else emptyMap()
  val before = commits.size
  commits = commits.filter { entry ->
    val (commit, change) = entry
    reviewTitles.entries.firstOrNull {
      // review with commit
      it.value.contains(commit.hash)
    }?.also {
      changesAlreadyInReview += change
      context.commitsAlreadyInReview += commit to it.key
    } == null
  }
  log("$projectId: ${before - commits.size} commits already in review")
  changesAlreadyInReview
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

private fun notifySlackChannel(investigator: Investigator?, context: Context) {
  val investigation = when {
    investigator == null -> ""
    investigator.isAssigned -> "Investigation is assigned to ${investigator.email}\n"
    else -> "Unable to assign investigation to ${investigator.email}\n"
  }
  val reaction = if (context.isFail()) ":scream:" else ":white_check_mark:"
  val build = "See <${thisBuildReportableLink()}|build log>"
  val text = "*${context.devRepoName}* $reaction\n" + investigation + build
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}