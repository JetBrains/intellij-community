package org.jetbrains.intellij.build.bazel

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches original content of BUILD.bazel files
 */
internal class BazelFilesLoader {
  private val contents = ConcurrentHashMap<Path, String>()

  fun getBuildFileContent(bazelFilePath: Path): String? {
    val content = contents.getOrPut(bazelFilePath) {
      runCatching { Files.readString(bazelFilePath) }.getOrNull() ?: NO_CONTENT_MARKER
    }
    return content.takeIf { it !== NO_CONTENT_MARKER }
  }
}

private val NO_CONTENT_MARKER = String(charArrayOf())