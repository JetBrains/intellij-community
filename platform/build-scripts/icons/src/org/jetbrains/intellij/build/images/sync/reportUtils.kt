// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.nio.file.Path

internal fun report(context: Context, skipped: Int): String {
  val (devIcons, icons) = context.devIcons.size to context.icons.size
  log("Skipped $skipped dirs")
  fun Collection<String>.logIcons(description: String) = "$size $description${if (size < 100) ": ${joinToString()}" else ""}"
  return when {
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
}

internal fun findCommitsToSync(context: Context) {
  if (context.doSyncDevRepo && context.devSyncRequired()) {
    context.iconsCommitsToSync = findCommitsByRepo(context, context.iconRepoDir, context.byDesigners)
  }
  if (context.doSyncIconsRepo && context.iconsSyncRequired()) {
    context.devCommitsToSync = findCommitsByRepo(context, context.devRepoDir, context.byDev)
  }
}

internal fun Map<Path, Collection<CommitInfo>>.commitMessage(): String =
  values.flatten().joinToString(separator = "\n\n") {
    it.subject + "\n" + "https://jetbrains.team/p/ij/repositories/IntelliJIcons/revision/${it.hash}"
  }

internal fun commit(context: Context) {
  if (context.iconsCommitsToSync.isEmpty()) {
    return
  }

  stageFiles(gitStatus(context.devRepoRoot).all(), context.devRepoRoot)
  if (gitStage(context.devRepoRoot).isEmpty()) {
    log("Nothing to commit")
    context.byDesigners.clear()
  }
  else {
    val user = triggeredBy()
    val branch = head(context.devRepoRoot)
    commit(branch, user.name, user.email, context.iconsCommitsToSync.commitMessage(), context.devRepoRoot)
  }
}

internal fun pushToIconsRepo(branch: String, context: Context): List<CommitInfo> =
  context.devCommitsToSync.values.flatten()
    .groupBy(CommitInfo::committer)
    .mapNotNull { (committer, commits) ->
      checkout(context.iconRepo, branch)
      commits.forEach { commit ->
        val change = context.byCommit[commit.hash] ?: error("Unable to find changes for commit ${commit.hash} by $committer")
        log("$committer syncing ${commit.hash} in ${context.iconsRepoName}")
        syncIconsRepo(context, change)
      }
      if (gitStage(context.iconRepo).isEmpty()) {
        log("Nothing to commit")
        context.byDev.clear()
        null
      }
      else {
        commitAndPush(branch, committer.name, committer.email,
                      commits.groupBy(CommitInfo::repo).commitMessage(), context.iconRepo)
      }
    }

private fun findCommitsByRepo(context: Context, root: Path, changes: Changes): Map<Path, Collection<CommitInfo>> {
  val commits = findCommits(context, root, changes)
  if (commits.isEmpty()) {
    return emptyMap()
  }
  log("${commits.size} commits found")
  return commits.map { it.key }.groupBy(CommitInfo::repo)
}

internal fun findRepo(file: Path) = findGitRepoRoot(file, silent = true)

private fun findCommits(context: Context, root: Path, changes: Changes) = changes.all()
  .mapNotNull { change ->
    val absoluteFile = root.resolve(change)
    val repo = findRepo(absoluteFile)
    val commit = latestChangeCommit(repo.relativize(absoluteFile).toString(), repo)
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
                          repo: Path): CommitInfo = run {
  execute(repo, GIT, "checkout", "-B", branch)
  commitAndPush(repo, branch, message, user, email)
}

private fun commit(branch: String, user: String,
                   email: String, message: String,
                   repo: Path) {
  execute(repo, GIT, "checkout", "-B", branch)
  commit(repo, message, user, email)
}

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
  val text = "*${context.devRepoName}* $reaction\n${message.replace("\"", "\\\"")}\n$build"
  val body = """{ "text": "$text" }"""
  val response = try {
    post(CHANNEL_WEB_HOOK, body, mediaType = null)
  }
  catch (e: Exception) {
    log("Post of '$body' has failed")
    throw e
  }
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response, body is '$body'")
}

internal fun slackLink(linkText: String, linkUrl: String) = "<$linkUrl|$linkText>"