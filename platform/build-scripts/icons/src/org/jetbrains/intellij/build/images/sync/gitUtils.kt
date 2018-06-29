// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

private val GIT = (System.getenv("TEAMCITY_GIT_PATH") ?: "git").also {
  val gitVersion = listOf("git", "--version").execute(File(System.getProperty("user.dir")), true)
  if (gitVersion.isBlank()) throw IllegalStateException("Git is not found")
  log(gitVersion)
}

/**
 * @param dirToList optional dir in [repo] from which to list files
 * @return map of file paths (relative to [dirToList]) to [GitObject]
 */
internal fun listGitObjects(
  repo: File, dirToList: String?,
  fileFilter: (File) -> Boolean = { true }
): Map<String, GitObject> = listGitTree(repo, dirToList, fileFilter).associate { it }

private fun listGitTree(
  repo: File, dirToList: String?,
  fileFilter: (File) -> Boolean
): Sequence<Pair<String, GitObject>> {
  val relativeDirToList = dirToList?.let {
    File(it).relativeTo(repo).path
  } ?: ""
  log("Inspecting $repo")
  return listOf(GIT, "ls-tree", "HEAD", "-r", relativeDirToList)
    .execute(repo).trim().lineSequence()
    .filter { it.isNotBlank() }.map { line ->
      // format: <mode> SP <type> SP <object> TAB <file>
      line.splitWithTab()
        .also { if (it.size != 2) throw IllegalStateException(line) }
        .let { it[0].splitWithSpace() + it[1] }
        .also { if (it.size != 4) throw IllegalStateException(line) }
    }
    .filter { fileFilter(repo.resolve(it[3])) }
    // <file>, <object>, repo
    .map { GitObject(it[3], it[2], repo) }
    .map { it.file.removePrefix("$relativeDirToList/") to it }
}

/**
 * @param repos multiple git repos from which to list files
 * @param root root repo
 * @return map of file paths (relative to [root]) to [GitObject]
 */
internal fun listGitObjects(
  root: File, repos: List<File>,
  fileFilter: (File) -> Boolean = { true }
): Map<String, GitObject> = repos.asSequence().flatMap { repo ->
  listGitTree(repo, null, fileFilter).map {
    // root relative <file> path to git object
    val rootRelativePath = repo.relativeTo(root).path
    if (rootRelativePath.isEmpty()) {
      it.first
    }
    else {
      "$rootRelativePath/${it.first}"
    } to it.second
  }
}.associate { it }

/**
 * @param file path relative to [repo]
 */
internal data class GitObject(val file: String, val hash: String, val repo: File) {
  fun getFile() = File(repo, file)
}

/**
 * @param path path in repo
 * @return root of repo
 */
internal fun findGitRepoRoot(path: String, silent: Boolean = false): File = File(path).let {
  if (it.isDirectory && it.listFiles().find {
      it.isDirectory && it.name == ".git"
    } != null) {
    if (!silent) log("Git repo found in $path")
    it
  }
  else if (it.parent != null) {
    if (!silent) log("No git repo found in $path")
    findGitRepoRoot(it.parent, silent)
  }
  else {
    throw IllegalArgumentException("No git repo found in $path")
  }
}

internal fun addChangesToGit(files: List<String>, repo: File) {
  // OS has argument length limit
  files.split(1000).forEach {
    (listOf(GIT, "add") + it).execute(repo, true)
  }
}

/**
 * see [https://stackoverflow.com/questions/8475448/find-merge-commit-which-include-a-specific-commit]
 *
 * @return latest commit (or merge) time
 */
internal fun latestChangeTime(file: String, repo: File): Long {
  // latest commit for file
  val commit = commitInfo(repo, "--", file)
  if (commit.timestamp <= 0) return -1
  // list commits that are both descendants of commit hash and ancestors of HEAD
  val ancestryPathList = listOf(GIT, "rev-list", "${commit.hash}..HEAD", "--ancestry-path")
    .execute(repo, true).lineSequence().filter { it.isNotBlank() }
  // follow only the first parent commit upon seeing a merge commit
  val firstParentList = listOf(GIT, "rev-list", "${commit.hash}..HEAD", "--first-parent")
    .execute(repo, true).lineSequence().filter { it.isNotBlank() }.toSet()
  // latest merge commit
  val mergeCommit = ancestryPathList
    // last common commit is the latest merge
    .lastOrNull { firstParentList.contains(it) }
    ?.let { commitInfo(repo, it) }
    // should be merge
    ?.takeIf { it.isMerge }
  return Math.max(commit.timestamp, mergeCommit?.timestamp ?: -1)
}

private fun commitInfo(repo: File, vararg args: String): CommitInfo {
  val output = listOf(GIT, "log", "--max-count", "1", "--format=%H/%cd/%P", "--date=raw", *args)
    .execute(repo, true).splitNotBlank("/")
  // <hash>/<timestamp> <timezone>/<parent hashes>
  return if (output.size != 3) {
    CommitInfo()
  }
  else {
    CommitInfo(
      hash = output[0],
      timestamp = output[1].splitWithSpace()[0].toLong(),
      isMerge = output[2].splitWithSpace().size > 1
    )
  }
}

private class CommitInfo(
  val hash: String = "",
  val timestamp: Long = -1,
  val isMerge: Boolean = false
)