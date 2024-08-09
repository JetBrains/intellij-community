// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.max

internal val GIT = (System.getenv("TEAMCITY_GIT_PATH") ?: System.getenv("GIT") ?: "git").also {
  val noGitFound = "Git is not found, please specify path to git executable in TEAMCITY_GIT_PATH or GIT or add it to PATH"
  try {
    val gitVersion = execute(null, it, "--version")
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
internal fun listGitObjects(repo: Path, dirToList: Path?, fileFilter: (Path) -> Boolean = { true }): Map<String, GitObject> {
  return listGitTree(repo, dirToList, fileFilter).collect(Collectors.toMap({ it.first }, { it.second }))
}

private fun listGitTree(repo: Path, dirToList: Path?, fileFilter: (Path) -> Boolean): Stream<Pair<String, GitObject>> {
  val relativeDirToList = dirToList?.toFile()?.relativeTo(repo.toFile())?.path ?: ""
  log("Inspecting $repo/$relativeDirToList")
  return execute(repo, GIT, "ls-tree", "HEAD", "-r", relativeDirToList)
    .trim().lines().stream()
    .filter(String::isNotBlank).map { line ->
      // format: <mode> SP <type> SP <object> TAB <file>
      line.splitWithTab()
        .also { if (it.size != 2) error(line) }
        .let { it[0].splitWithSpace() + it[1] }
        .also { if (it.size != 4) error(line) }
    }
    .filter { fileFilter(repo.resolve(it[3].removeSuffix("\"").removePrefix("\""))) }
    // <file>, <object>, repo
    .map { GitObject(it[3], it[2], repo) }
    .map { it.path.removePrefix("$relativeDirToList/") to it }
}

/**
 * @param path path relative to [repo]
 */
internal data class GitObject(val path: String, val hash: String, val repo: Path) {
  val file: Path = repo.resolve(path)
}

/**
 * @param dir path in repo
 * @return root of repo
 */
internal fun findGitRepoRoot(dir: Path, silent: Boolean = false): Path {
  return when {
    Files.isDirectory(dir) && dir.toFile().listFiles()?.find { file ->
      file.isDirectory && file.name == ".git"
    } != null -> {
      if (!silent) {
        log("Git repo found in $dir")
      }
      dir
    }
    dir.parent != null -> {
      if (!silent) {
        log("No git repo found in $dir")
      }
      findGitRepoRoot(dir.parent, silent)
    }
    else -> error("No git repo found in $dir")
  }
}

internal fun cleanup(repo: Path) {
  execute(repo, GIT, "reset", "--hard")
  execute(repo, GIT, "clean", "-xfd")
}

internal fun stageFiles(files: List<String>, repo: Path) {
  // OS has argument length limit
  splitAndTry(1000, files, repo) {
    execute(repo, GIT, "add", "--no-ignore-removal", "--ignore-errors", *it.toTypedArray())
  }
}

private fun splitAndTry(factor: Int, files: List<String>, repo: Path, block: (files: List<String>) -> Unit) {
  files.split(factor).forEach {
    try {
      block(it)
    }
    catch (e: Exception) {
      if (e.message?.contains("did not match any files") == true) return
      val finerFactor: Int = factor / 2
      if (finerFactor < 1) throw e
      log("Git add command failed with ${e.message}")
      splitAndTry(finerFactor, files, repo, block)
    }
  }
}

internal fun commit(repo: Path, message: String) {
  execute(repo, GIT, "commit", "-m", message)
}

internal fun commit(repo: Path, message: String, user: String, email: String) {
  execute(
    repo, GIT,
    "-c", "user.name=$user",
    "-c", "user.email=$email",
    "commit", "-m", message,
    "--author=$user <$email>"
  )
}

internal fun commitAndPush(repo: Path, branch: String, message: String, user: String, email: String, force: Boolean = false): CommitInfo {
  commit(repo, message, user, email)
  push(repo, branch, user, email, force)
  return commitInfo(repo) ?: error("Unable to read last commit")
}

internal fun checkout(repo: Path, branch: String) = execute(repo, GIT, "checkout", "-B", branch)

internal fun push(repo: Path, spec: String, user: String? = null, email: String? = null, force: Boolean = false) =
  retry(doRetry = { beforePushRetry(it, repo, spec, user, email) }) {
    var args = arrayOf("origin", spec)
    if (force) args += "--force"
    execute(repo, GIT, "push", *args, withTimer = true)
  }

private fun beforePushRetry(e: Throwable, repo: Path, spec: String, user: String?, email: String?): Boolean {
  if (!isGitServerUnavailable(e)) {
    val specParts = spec.split(':')
    val identity = if (user != null && email != null) arrayOf(
      "-c", "user.name=$user",
      "-c", "user.email=$email"
    )
    else emptyArray()
    execute(repo, GIT, *identity, "pull", "--rebase=true", "origin", if (specParts.count() == 2) {
      "${specParts[1]}:${specParts[0]}"
    }
    else spec, withTimer = true)
  }
  return true
}

private fun isGitServerUnavailable(e: Throwable) = with(e.message ?: "") {
  contains("remote end hung up unexpectedly")
  || contains("Service is in readonly mode")
  || contains("failed to lock")
  || contains("Connection timed out")
}

@Volatile
private var origins = emptyMap<Path, String>()
private val originsGuard = Any()

internal fun getOriginUrl(repo: Path): String {
  if (!origins.containsKey(repo)) {
    synchronized(originsGuard) {
      if (!origins.containsKey(repo)) {
        origins = origins + (repo to execute(repo, GIT, "ls-remote", "--get-url", "origin")
          .removeSuffix(System.lineSeparator())
          .trim())
      }
    }
  }
  return origins.getValue(repo)
}

@Volatile
private var latestChangeCommits = emptyMap<String, CommitInfo>()
private val latestChangeCommitsGuard = Any()

/**
 * @param path path relative to [repo]
 */
internal fun latestChangeCommit(path: String, repo: Path): CommitInfo? {
  val file = repo.resolve(path).toAbsolutePath().toString()
  if (!latestChangeCommits.containsKey(file)) {
    synchronized(file) {
      if (!latestChangeCommits.containsKey(file)) {
        val commitInfo = pathInfo(repo, "--", path)
        if (commitInfo != null) {
          synchronized(latestChangeCommitsGuard) {
            latestChangeCommits = latestChangeCommits + (file to commitInfo)
          }
        }
        else return null
      }
    }
  }
  return latestChangeCommits.getValue(file)
}

/**
 * @return latest commit (or merge) time
 */
internal fun latestChangeTime(path: String, repo: Path): Long {
  // latest commit for file
  val commit = latestChangeCommit(path, repo)
  if (commit == null) return -1
  val mergeCommit = findMergeCommit(repo, commit.hash)
  return max(commit.timestamp, mergeCommit?.timestamp ?: -1)
}

/**
 * see [https://stackoverflow.com/questions/8475448/find-merge-commit-which-include-a-specific-commit]
 */
private fun findMergeCommit(repo: Path, commit: String, searchUntil: String = "HEAD"): CommitInfo? {
  // list commits that are both descendants of commit hash and ancestors of HEAD
  val ancestryPathList = execute(repo, GIT, "rev-list", "$commit..$searchUntil", "--ancestry-path")
    .lineSequence().filter { it.isNotBlank() }
  // follow only the first parent commit upon seeing a merge commit
  val firstParentList = execute(repo, GIT, "rev-list", "$commit..$searchUntil", "--first-parent")
    .lineSequence().filter { it.isNotBlank() }.toSet()
  // last common commit may be the latest merge
  return ancestryPathList
    .lastOrNull(firstParentList::contains)
    ?.let { commitInfo(repo, it) }
    ?.takeIf {
      // should be a merge
      it.parents.size > 1 &&
      // but not some branch merge right after [commit]
      it.parents.first() != commit
    }?.let {
      when {
        it.parents.size > 2 -> {
          log("WARNING: Merge commit ${it.hash} for $commit in $repo is found but it has more than two parents (one of them could be master), skipping")
          null
        }
        // merge is found
        else -> it
      }
    }
}

@Volatile
private var heads = emptyMap<Path, String>()
private val headsGuard = Any()

internal fun head(repo: Path): String {
  if (!heads.containsKey(repo)) {
    synchronized(headsGuard) {
      if (!heads.containsKey(repo)) {
        heads = heads + (repo to execute(repo, GIT, "rev-parse", "--abbrev-ref", "HEAD").removeSuffix(System.lineSeparator()))
      }
    }
  }
  return heads.getValue(repo)
}

internal fun commitInfo(repo: Path, vararg args: String) = gitLog(repo, *args).singleOrNull()

private fun pathInfo(repo: Path, vararg args: String) = gitLog(repo, "--follow", *args).singleOrNull()

private fun gitLog(repo: Path, vararg args: String): List<CommitInfo> {
  return execute(
    repo, GIT, "log",
    "--max-count", "1",
    "--format=%H/%cd/%P/%cn/%ce/%s",
    "--date=raw", *args
  ).lineSequence().mapNotNull {
    val output = it.splitNotBlank("/")
    // <hash>/<timestamp> <timezone>/<parent hashes>/committer email/<subject>
    if (output.size >= 6) {
      CommitInfo(
        repo = repo,
        hash = output[0],
        timestamp = output[1].splitWithSpace()[0].toLong(),
        parents = output[2].splitWithSpace(),
        committer = Committer(name = output[3], email = output[4]),
        subject = output.subList(5, output.size)
          .joinToString(separator = "/")
          .removeSuffix(System.lineSeparator())
      )
    }
    else null
  }.toList()
}

internal data class CommitInfo(
  val hash: String,
  val timestamp: Long,
  val subject: String,
  val committer: Committer,
  val parents: List<String>,
  val repo: Path
)

internal data class Committer(val name: String, val email: String)

internal fun gitStatus(repo: Path, includeUntracked: Boolean = false) = Changes().apply {
  execute(repo, GIT, "status", "--short", "--untracked-files=${if (includeUntracked) "all" else "no"}", "--ignored=no")
    .lineSequence()
    .filter(String::isNotBlank)
    .forEach {
      val (status, path) = it.splitToSequence("->", " ")
        .filter(String::isNotBlank)
        .map(String::trim)
        .toList()
      val type = when(status) {
        "A", "??" -> Changes.Type.ADDED
        "M", "MM" -> Changes.Type.MODIFIED
        "D" -> Changes.Type.DELETED
        else -> error("Unknown change type: $status. Git status line: $it")
      }
      register(type, listOf(path))
    }
}

internal fun gitStage(repo: Path) = execute(repo, GIT, "diff", "--cached", "--name-status")

internal fun changesFromCommit(repo: Path, hash: String): Map<Changes.Type, List<String>> {
  return execute(repo, GIT, "show", "--pretty=format:none", "--name-status", "--no-renames", hash)
    .lineSequence().map { it.trim() }
    .filter { it.isNotEmpty() && it != "none" }
    .map { it.splitWithTab() }
    .onEach { if (it.size != 2) error(it.joinToString(" ")) }
    .map {
      val (type, path) = it
      when (type) {
        "A" -> Changes.Type.ADDED
        "D" -> Changes.Type.DELETED
        "M" -> Changes.Type.MODIFIED
        "T" -> Changes.Type.MODIFIED
        else -> return@map null
      } to path
    }
    .filterNotNull().groupBy({ it.first }, { it.second })
}

internal fun gitClone(uri: String, dir: Path): Path {
  val filesBeforeClone = dir.toFile().listFiles()?.toList() ?: emptyList()
  execute(dir, GIT, "clone", uri)
  return ((dir.toFile().listFiles()?.toList() ?: emptyList()) - filesBeforeClone).first {
    uri.contains(it.name)
  }.toPath()
}
