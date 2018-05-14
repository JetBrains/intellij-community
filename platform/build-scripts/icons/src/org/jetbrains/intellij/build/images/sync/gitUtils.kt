// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal val GIT = System.getenv("TEAMCITY_GIT_PATH") ?: "git"

/**
 * @param dirToList optional dir in [repo] from which to list files
 * @return map of file paths (relative to [dirToList]) to git hashes
 */
internal fun listGitObjects(
  repo: File, dirToList: String?,
  fileFilter: (File) -> Boolean = { true }
): Map<String, String> = listGitTree(repo, dirToList, fileFilter).associate { it }

private fun listGitTree(
  repo: File, dirToList: String?,
  fileFilter: (File) -> Boolean
): Sequence<Pair<String, String>> {
  val relativeDirToList = dirToList?.let {
    File(it).relativeTo(repo).path
  } ?: ""
  log("Inspecting $repo")
  val lsTree = "$GIT ls-tree HEAD -r $relativeDirToList".execute(repo)
  return lsTree.trim().lineSequence()
    .filter { it.isNotBlank() }.map { line ->
      // format: <mode> SP <type> SP <object> TAB <file>
      line.splitWithTab()
        .also { if (it.size != 2) throw IllegalStateException(line) }
        .let { it[0].splitWithSpace() + it[1] }
        .also { if (it.size != 4) throw IllegalStateException(line) }
        .toMutableList()
    }
    .filter { fileFilter(repo.resolve(it[3])) }
    .onEach { it[3] = it[3].removePrefix("$relativeDirToList/") }
    // <file> to <object>
    .map { it[3] to it[2] }
}

/**
 * @param repos multiple git repos from which to list files
 * @param root root repo
 * @return map of file paths (relative to [root]) to git hashes
 */
internal fun listGitObjects(
  root: File, repos: List<File>,
  fileFilter: (File) -> Boolean = { true }
): Map<String, String> = repos.asSequence().flatMap { repo ->
  listGitTree(repo, null, fileFilter).map {
    // root relative <file> path to <object>
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
 * @param path path in repo
 * @return root of repo
 */
internal fun findGitRepoRoot(path: String): File = File(path).let {
  if (it.isDirectory && it.listFiles().find {
      it.isDirectory && it.name == ".git"
    } != null) {
    log("Git repo found in $path")
    it
  }
  else if (it.parent != null) {
    log("No git repo found in $path")
    findGitRepoRoot(it.parent)
  }
  else {
    throw IllegalArgumentException("No git repo found in $path")
  }
}