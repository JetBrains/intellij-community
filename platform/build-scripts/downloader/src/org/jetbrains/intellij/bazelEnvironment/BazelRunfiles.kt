// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.org.jetbrains.intellij.bazelEnvironment

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.useLines

object BazelRunfiles {
  /**
   * Running from `bazel test` or `bazel run`
   */
  val isRunningFromBazel: Boolean = System.getenv(JAVA_RUNFILES_ENV_NAME) != null

  @JvmStatic
  fun getFileByLabel(label: BazelLabel): Path {
    val repoEntry = bazelTestRepoMapping.getOrElse(label.repo) {
      error("Unable to determine dependency path '${label.asLabel}'")
    }

    // Build a single relative key used both for runfiles tree and for manifest lookup
    val manifestKey = buildString {
      append(repoEntry.runfilesRelativePath)
      if (label.packageName.isNotEmpty()) {
        append('/')
        append(label.packageName)
      }
      append('/')
      append(label.target)
    }

    // On Windows there could be no runfiles tree, so we need to use manifest only
    if (runfilesManifestOnly || BazelRunfilesManifest.exists) {
      val resolved = Path.of(BazelRunfilesManifest.get(manifestKey))
      check(resolved.exists()) { "Unable to find dependency ($RUNFILES_MANIFEST_ONLY_ENV_NAME=1) '${label.asLabel}' at $resolved" }
      return resolved
    }

    // Locate file under runfiles tree
    val resolved = bazelJavaRunfilesPath.resolve(manifestKey)
    check(resolved.exists()) { "Unable to find dependency '${label.asLabel}' at $resolved" }
    return resolved
  }

  /**
   * Tests under community root may run in community (OSS) or in the ultimate monorepo context.
   *
   * Under ultimate monorepo Bazel project, workspace for test dependencies is named `community+`,
   * while when run under community Bazel project, it's named `_main`.
   *
   * This function finds `relativePath` under one of them, depending on current project.
   * It fails when the directory can't be found or there is an ambiguity.
   *
   * see https://bazel.build/reference/be/common-definitions#typical-attributes (check `data`)
   *
   * see https://bazel.build/reference/test-encyclopedia#initial-conditions
   */
  @JvmStatic
  fun findRunfilesDirectoryUnderCommunityOrUltimate(relativePath: String): Path {
    val (root1, root2) = if (runfilesManifestOnly) {
      val root1key = "community+/${relativePath}"
      val root2key = "_main/${relativePath}"
      Path.of(BazelRunfilesManifest.get(root1key)) to
        Path.of(BazelRunfilesManifest.get(root2key))
    } else {
      bazelJavaRunfilesPath.resolve("community+").resolve(relativePath) to
        bazelJavaRunfilesPath.resolve("_main").resolve(relativePath)
    }

    val root1exists = root1.isDirectory()
    val root2exists = root2.isDirectory()
    if (!root1exists && !root2exists) {
      error("Cannot find runfiles directory $relativePath under community+ or _main. " +
            "JAVA_RUNFILES (runfiles root) = ${bazelJavaRunfilesPath}. " +
            "Tried $root1 and $root2. " +
            "Please check that you passed this directory via data attribute of test rule")
    }
    if (root1exists && root2exists) {
      error("Both $root1 and $root2 exist. " +
            "Meaning $relativePath is available both under community and ultimate roots. " +
            "This ambitious setup might cause problems. " +
            "Please remove $root1 or $root2 or use a different relative path for test rule")
    }
    return if (root1exists) root1 else root2
  }

  // Bazel sets RUNFILES_MANIFEST_ONLY=1 on platforms that only support manifest-based runfiles (e.g., Windows).
  // Cache it to avoid repeated env lookups and branching cost in hot paths.
  private val runfilesManifestOnly: Boolean by lazy {
    val v = System.getenv(RUNFILES_MANIFEST_ONLY_ENV_NAME)
    v != null && v.isNotBlank() && v == "1"
  }

  /**
   * Absolute path to the base of the runfiles tree (your test dependencies too),
   * see [Test encyclopedia](https://bazel.build/reference/test-encyclopedia#initial-conditions)
   */
  @JvmStatic
  val bazelJavaRunfilesPath: Path by lazy {
    val value = System.getenv(JAVA_RUNFILES_ENV_NAME)
    if (value == null) {
      error("Not running under `bazel test` or `bazel run` because $JAVA_RUNFILES_ENV_NAME env is not set.")
    }
    val path = Path.of(value).absolute()
    if (!path.exists()) {
      error("Bazel test env '$JAVA_RUNFILES_ENV_NAME' points to non-existent directory: $path")
    }
    if (!path.isDirectory()) {
      error("Bazel test env '$JAVA_RUNFILES_ENV_NAME' points to non-directory: $path")
    }
    path
  }

  /**
   * https://fuchsia.googlesource.com/fuchsia/+/HEAD/build/bazel/BAZEL_RUNFILES.md
   * repo -> (repo, path) based on _repo_mapping file to resolve as a subdirectory of bazelTestRunfilesPath
   */
  @JvmStatic
  val bazelTestRepoMapping: Map<String, RepoMappingEntry> by lazy {
    val repoMappingFile = when {
      bazelJavaRunfilesPath.resolve("_repo_mapping").exists() -> bazelJavaRunfilesPath.resolve("_repo_mapping")
      Path.of(BazelRunfilesManifest.get("_repo_mapping")).exists() ->
        Path.of(BazelRunfilesManifest.get("_repo_mapping"))
      else -> error("repo_mapping file not found.")
    }
    repoMappingFile.useLines { lines ->
      lines
        .filter { it.isNotBlank() && it.isNotEmpty() }
        .map { parseRepoEntry(it) }
        .distinct()
        .associateBy { it.repoName }
    }.plus(
      "" to RepoMappingEntry("", "_main")
    )
  }

  data class RepoMappingEntry(val repoName: String, val runfilesRelativePath: String)

  private fun parseRepoEntry(line: String): RepoMappingEntry {
    val parts = line.split(",", limit = 3)
    require(parts.size == 3) { "_repo_mapping line must have exactly 3 comma-separated values: '$line'" }
    return RepoMappingEntry( parts[1], parts[2])
  }

  private const val RUNFILES_MANIFEST_ONLY_ENV_NAME = "RUNFILES_MANIFEST_ONLY"
  private const val JAVA_RUNFILES_ENV_NAME = "JAVA_RUNFILES"
}
