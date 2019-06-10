// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.*
import java.util.stream.Collectors
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
  if (context.doSyncDevRepo && context.devSyncRequired()) {
    context.iconsCommitsToSync = findCommitsByRepo(context, UPSOURCE_DEV_PROJECT_ID, context.iconsRepoDir, context.byDesigners)
  }
  if (context.doSyncIconsRepo && context.iconsSyncRequired()) {
    context.devCommitsToSync = findCommitsByRepo(context, UPSOURCE_ICONS_PROJECT_ID, context.devRepoDir, context.byDev)
  }
}

private fun Map<File, Collection<CommitInfo>>.commitMessage() = "Icons from ${entries.joinToString { entry ->
  "${entry.value.joinToString { it.hash.substring(0..8) }}: ${getOriginUrl(entry.key)}"
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

internal fun createReviewForDev(context: Context): Review? {
  if (context.iconsCommitsToSync.isEmpty()) return null
  val repos = context.iconsChanges().map {
    findRepo(context.devRepoRoot.resolve(it))
  }.distinct()
  verifyDevIcons(context, repos)
  if (repos.all { gitStage(it).isEmpty() }) {
    log("Nothing to commit")
    context.byDesigners.clear()
    return null
  }
  val user = triggeredBy()
  val master = repos.parallelStream().map(::head).collect(Collectors.toSet()).single()
  return withTmpBranch(repos, master) { branch ->
    val commitsForReview = commitAndPush(branch, user.name, user.email, context.iconsCommitsToSync.commitMessage(), repos)
    val projectId = UPSOURCE_DEV_PROJECT_ID
    if (projectId.isNullOrEmpty()) {
      log("WARNING: unable to create Upsource review for ${context.devRepoName}, just plain old branch review")
      PlainOldReview(branch, projectId)
    }
    else {
      val review = createReview(projectId, branch, master, commitsForReview.map(CommitInfo::hash))
      try {
        addReviewer(projectId, review, user.email)
        postVerificationResultToReview(review)
        removeReviewer(projectId, review, UpsourceUser.email)
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

internal fun pushToIconsRepo(context: Context): Collection<CommitInfo> {
  val repos = listOf(context.iconsRepo)
  val master = head(context.iconsRepo)
  return context.devCommitsToSync.values.flatten()
    .groupBy(CommitInfo::committer)
    .flatMap { (committer, commits) ->
      repos.parallelStream().forEach { checkout(it, master) }
      commits.forEach { commit ->
        val change = context.byCommit[commit.hash] ?: error("Unable to find changes for commit ${commit.hash} by $committer")
        log("$committer syncing ${commit.hash} in ${context.iconsRepoName}")
        syncIconsRepo(context, change)
      }
      if (gitStage(context.iconsRepo).isEmpty()) {
        log("Nothing to commit")
        context.byDev.clear()
        emptyList()
      }
      else {
        commitAndPush(master, committer.name, committer.email,
                      commits.groupBy(CommitInfo::repo).commitMessage(), repos)
      }
    }
}

private fun findCommitsByRepo(context: Context, projectId: String?, root: File, changes: Changes
): Map<File, Collection<CommitInfo>> {
  val commits = findCommits(context, root, changes)
  if (commits.isEmpty()) return emptyMap()
  log("$projectId: ${commits.size} commits found")
  return commits.map { it.key }.groupBy(CommitInfo::repo)
}

@Volatile
private var reposMap = emptyMap<File, File>()
private val reposMapGuard = Any()
internal fun findRepo(file: File): File {
  if (!reposMap.containsKey(file)) synchronized(reposMapGuard) {
    if (!reposMap.containsKey(file)) {
      reposMap = reposMap + (file to findGitRepoRoot(file, silent = true))
    }
  }
  return reposMap.getValue(file)
}

private fun findCommits(context: Context, root: File, changes: Changes) = changes.all()
  .mapNotNull { change ->
    val absoluteFile = root.resolve(change)
    val repo = findRepo(absoluteFile)
    val commit = latestChangeCommit(absoluteFile.toRelativeString(repo), repo)
    if (commit != null) commit to change else null
  }.onEach {
    val commit = it.first.hash
    val change = it.second
    if (!context.byCommit.containsKey(commit)) context.byCommit[commit] = Changes(changes.includeRemoved)
    val commitChange = context.byCommit.getValue(commit)
    when {
      changes.added.contains(change) -> commitChange.added += change
      changes.modified.contains(change) -> commitChange.modified += change
      changes.removed.contains(change) -> commitChange.removed += change
    }
  }.groupBy({ it.first }, { it.second })

private fun commitAndPush(branch: String, user: String,
                          email: String, message: String,
                          repos: Collection<File>) = repos.parallelStream().map {
  execute(it, GIT, "checkout", "-B", branch)
  commitAndPush(it, branch, message, user, email)
}.toList()

private val CHANNEL_WEB_HOOK = System.getProperty("intellij.icons.slack.channel")

internal fun notifySlackChannel(investigator: Investigator, context: Context) {
  val investigation = if (investigator.isAssigned) {
    "Investigation is assigned to ${investigator.email}"
  }
  else "Unable to assign investigation to ${investigator.email}"
  notifySlackChannel(investigation, context, success = false)
}

internal fun notifySlackChannel(message: String, context: Context, success: Boolean) {
  val reaction = if (success) ":white_check_mark:" else ":sadfrog:"
  val build = "See ${slackLink("build log", thisBuildReportableLink())}"
  val text = "*${context.devRepoName}* $reaction\n$message\n$build"
  val response = post(CHANNEL_WEB_HOOK, """{ "text": "$text" }""")
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response")
}

internal fun slackLink(linkText: String, linkUrl: String) = "<$linkUrl|$linkText>"