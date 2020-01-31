// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.stream.Collectors
import kotlin.streams.toList

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
    context.iconsCommitsToSync = findCommitsByRepo(context, context.iconsRepoDir, context.byDesigners)
  }
  if (context.doSyncIconsRepo && context.iconsSyncRequired()) {
    context.devCommitsToSync = findCommitsByRepo(context, context.devRepoDir, context.byDev)
  }
}

internal fun Map<File, Collection<CommitInfo>>.commitMessage() =
  entries.joinToString("\n\n") { entry ->
    entry.value.joinToString("\n") {
      "'${it.subject}' from ${it.hash.substring(0..8)}"
    } + " from ${getOriginUrl(entry.key)}"
  }

internal fun commitAndPush(context: Context) {
  if (context.iconsCommitsToSync.isEmpty()) return
  val repos = context.iconsChanges().map {
    findRepo(context.devRepoRoot.resolve(it))
  }.distinct()
  verifyDevIcons(context, repos)
  if (repos.all { gitStage(it).isEmpty() }) {
    log("Nothing to commit")
    context.byDesigners.clear()
  }
  else {
    val user = triggeredBy()
    val branch = repos.parallelStream().map(::head).collect(Collectors.toSet()).single()
    commitAndPush(branch, user.name, user.email, context.iconsCommitsToSync.commitMessage(), repos)
  }
}

private fun verifyDevIcons(context: Context, repos: Collection<File>) {
  context.verifyDevIcons(repos)
  repos.forEach { repo ->
    stageFiles(gitStatus(repo).all(), repo)
  }
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

private fun findCommitsByRepo(context: Context, root: File, changes: Changes
): Map<File, Collection<CommitInfo>> {
  val commits = findCommits(context, root, changes)
  if (commits.isEmpty()) return emptyMap()
  log("${commits.size} commits found")
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
  val text = "*${context.devRepoName}* $reaction\n${message.replace("\"", "\\\"")}\n$build"
  val body = """{ "text": "$text" }"""
  val response = try {
    post(CHANNEL_WEB_HOOK, body)
  } catch (e: Exception) {
    log("Post of '$body' has failed")
    throw e
  }
  if (response != "ok") error("$CHANNEL_WEB_HOOK responded with $response, body is '$body'")
}

internal fun slackLink(linkText: String, linkUrl: String) = "<$linkUrl|$linkText>"