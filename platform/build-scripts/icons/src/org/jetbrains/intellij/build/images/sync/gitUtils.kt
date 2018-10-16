// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.io.IOException
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

private val GIT = (System.getenv("TEAMCITY_GIT_PATH") ?: System.getenv("GIT") ?: "git").also {
  val noGitFound = "Git is not found, please specify path to git executable in TEAMCITY_GIT_PATH or GIT or add it to PATH"
  try {
    val gitVersion = execute(File(System.getProperty("user.dir")), it, "--version", silent = true)
    if (gitVersion.isBlank()) error(noGitFound)
    log(gitVersion)
  }
  catch (e: IOException) {
    throw IllegalStateException(noGitFound, e)
  }
}

/**
 * @param dirToList optional dir in [repo] from which to list files
 * @return map of file paths (relative to [dirToList]) to [GitObject]
 */
internal fun listGitObjects(
  repo: File, dirToList: String?,
  fileFilter: (File) -> Boolean = { true }
): Map<String, GitObject> = listGitTree(repo, dirToList, fileFilter)
  .collect(Collectors.toMap({ it.first }, { it.second }))

private fun listGitTree(
  repo: File, dirToList: String?,
  fileFilter: (File) -> Boolean
): Stream<Pair<String, GitObject>> {
  val relativeDirToList = dirToList?.let {
    File(it).relativeTo(repo).path
  } ?: ""
  log("Inspecting $repo")
  if (BUILD_SERVER == null) try {
    execute(repo, GIT, "pull", "--rebase")
  }
  catch (e: Exception) {
    callSafely {
      execute(repo, GIT, "rebase", "--abort")
    }
    log("Unable to pull changes for $repo")
  }
  return execute(repo, GIT, "ls-tree", "HEAD", "-r", relativeDirToList, silent = true)
    .trim().lines().stream()
    .filter { it.isNotBlank() }.map { line ->
      // format: <mode> SP <type> SP <object> TAB <file>
      line.splitWithTab()
        .also { if (it.size != 2) error(line) }
        .let { it[0].splitWithSpace() + it[1] }
        .also { if (it.size != 4) error(line) }
    }
    .filter { fileFilter(repo.resolve(it[3])) }
    // <file>, <object>, repo
    .map { GitObject(it[3], it[2], repo) }
    .map { it.path.removePrefix("$relativeDirToList/") to it }
}

/**
 * @param repos multiple git repos from which to list files
 * @param root root repo
 * @return map of file paths (relative to [root]) to [GitObject]
 */
internal fun listGitObjects(
  root: File, repos: List<File>,
  fileFilter: (File) -> Boolean = { true }
): Map<String, GitObject> = repos.parallelStream().flatMap { repo ->
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
}.collect(Collectors.toMap({ it.first }, { it.second }))

/**
 * @param path path relative to [repo]
 */
internal data class GitObject(val path: String, val hash: String, val repo: File) {
  val file = File(repo, path)
}

/**
 * @param path path in repo
 * @return root of repo
 */
internal fun findGitRepoRoot(path: String, silent: Boolean = false): File {
  val dir = File(path)
  return if (dir.isDirectory && dir.listFiles().find { file ->
      file.isDirectory && file.name == ".git"
    } != null) {
    if (!silent) log("Git repo found in $path")
    dir
  }
  else if (dir.parent != null) {
    if (!silent) log("No git repo found in $path")
    findGitRepoRoot(dir.parent, silent)
  }
  else {
    error("No git repo found in $path")
  }
}

internal fun addChangesToGit(files: List<String>, repo: File) {
  // OS has argument length limit
  splitAndTry(1000, files, repo)
}

private fun splitAndTry(factor: Int, files: List<String>, repo: File) {
  log("Executing git add for ${repo.absolutePath} in batches with $factor elements each")
  files.split(factor).forEach {
    try {
      execute(repo, GIT, "add", *it.toTypedArray(), silent = true)
    }
    catch (e: Exception) {
      val finerFactor: Int = factor / 2
      if (finerFactor < 1) throw e
      log("Git add command failed with ${e.message}")
      splitAndTry(finerFactor, files, repo)
    }
  }
}

@Volatile
private var latestChangeCommits = emptyMap<String, CommitInfo>()
private val latestChangeCommitsGuard = Any()

internal fun latestChangeCommit(path: String, repo: File? = null): CommitInfo? {
  val foundRepo = repo ?: findGitRepoRoot(path, silent = true)
  val file = (if (repo == null) File(path) else File(repo, path)).canonicalPath
  if (!latestChangeCommits.containsKey(file)) {
    synchronized(file) {
      if (!latestChangeCommits.containsKey(file)) {
        val commitInfo = commitInfo(foundRepo, "--", path)
        if (commitInfo != null) {
          synchronized(latestChangeCommitsGuard) {
            val tmp = HashMap(latestChangeCommits)
            tmp[file] = commitInfo
            latestChangeCommits = tmp
          }
        }
        else return null
      }
    }
  }
  return latestChangeCommits[file]
}

/**
 * @return latest commit (or merge) time
 */
internal fun latestChangeTime(path: String, repo: File): Long {
  // latest commit for file
  val commit = latestChangeCommit(path, repo)
  if (commit == null) return -1
  val mergeCommit = findMergeCommit(repo, commit.hash)
  return Math.max(commit.timestamp, mergeCommit?.timestamp ?: -1)
}

/**
 * see [https://stackoverflow.com/questions/8475448/find-merge-commit-which-include-a-specific-commit]
 */
private fun findMergeCommit(repo: File, commit: String, searchUntil: String = "HEAD"): CommitInfo? {
  // list commits that are both descendants of commit hash and ancestors of HEAD
  val ancestryPathList = execute(repo, GIT, "rev-list", "$commit..$searchUntil", "--ancestry-path", silent = true)
    .lineSequence().filter { it.isNotBlank() }
  // follow only the first parent commit upon seeing a merge commit
  val firstParentList = execute(repo, GIT, "rev-list", "$commit..$searchUntil", "--first-parent", silent = true)
    .lineSequence().filter { it.isNotBlank() }.toSet()
  // last common commit may be the latest merge
  return ancestryPathList
    .lastOrNull { firstParentList.contains(it) }
    ?.let { commitInfo(repo, it) }
    ?.takeIf {
      // should be merge
      it.parents.size > 1 &&
      // but not some branch merge right after [commit]
      it.parents.first() != commit
    }?.let {
      when {
        // if it's a merge of master into master then all parents belong to master but the first one doesn't lead to [commit]
        isMergeOfMasterIntoMaster(repo, it) -> findMergeCommit(repo, commit, it.parents[1])
        it.parents.size > 2 -> {
          log("WARNING: Merge commit ${it.hash} for $commit in $repo is found but it has more than two parents (one of them could be master), skipping")
          null
        }
        // merge is found
        else -> it
      }
    }
}

/**
 * Inspecting commit subject which isn't reliable criteria, may need to be adjusted
 *
 * @param merge merge commit
 */
private fun isMergeOfMasterIntoMaster(repo: File, merge: CommitInfo) =
  merge.parents.size == 2 && with(merge.subject) {
    val head = head(repo)
    (contains("Merge branch $head") ||
     contains("Merge branch '$head'") ||
     contains("origin/$head")) &&
    (!contains(" into ") ||
     endsWith("into $head") ||
     endsWith("into '$head'"))
  }


@Volatile
private var heads = emptyMap<File, String>()
private val headsGuard = Any()

private fun head(repo: File): String {
  if (!heads.containsKey(repo)) {
    synchronized(headsGuard) {
      if (!heads.containsKey(repo)) {
        val tmp = HashMap(heads)
        tmp[repo] = execute(repo, GIT, "rev-parse", "--abbrev-ref", "HEAD", silent = true).removeSuffix(System.lineSeparator())
        heads = tmp
      }
    }
  }
  return heads[repo]!!
}

private fun commitInfo(repo: File, vararg args: String): CommitInfo? {
  val output = execute(repo, GIT, "log", "--max-count", "1", "--format=%H/%cd/%P/%ce/%s", "--date=raw", *args, silent = true).splitNotBlank("/")
  // <hash>/<timestamp> <timezone>/<parent hashes>/committer email/<subject>
  return if (output.size >= 5) {
    CommitInfo(
      hash = output[0],
      timestamp = output[1].splitWithSpace()[0].toLong(),
      parents = output[2].splitWithSpace(),
      committerEmail = output[3],
      subject = output.subList(4, output.size)
        .joinToString(separator = "/")
        .removeSuffix(System.lineSeparator())
    )
  }
  else null
}

internal class CommitInfo(
  val hash: String,
  val timestamp: Long,
  val subject: String,
  val parents: List<String>,
  val committerEmail: String
)