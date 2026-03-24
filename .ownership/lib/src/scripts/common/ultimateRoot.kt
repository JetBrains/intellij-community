package com.intellij.codeowners.scripts.common

import java.nio.file.Files
import java.nio.file.Path

private const val BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV = "BUILD_WORKSPACE_DIRECTORY"

val ultimateRoot: Path by lazy {
  val workspaceDir: Path? = System.getenv(BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV)?.let { Path.of(it).normalize() }
  val userDir: Path = Path.of(System.getProperty("user.dir"))
  searchRepositoryRoot(workspaceDir ?: userDir)
}

private fun searchRepositoryRoot(start: Path): Path {
  var current = start
  while (true) {
    if (Files.exists(current.resolve(".patronus")) && Files.exists(current.resolve(".ultimate.root.marker"))) {
      return current
    }

    current = checkNotNull(current.parent) { "Cannot find ultimate root starting from $start" }
  }
}