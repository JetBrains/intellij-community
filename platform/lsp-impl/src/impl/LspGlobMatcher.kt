package com.intellij.platform.lsp.impl

import com.intellij.openapi.util.getPathMatcher
import java.nio.file.Path
import java.nio.file.PathMatcher

internal class LspGlobMatcher {
  private val patternToPathMatcherCache: MutableMap<String, PathMatcher> = hashMapOf()

  fun pathMatches(path: String, isDirectory: Boolean, globPattern: String, basePath: String?): Boolean {
    val pathToMatch = when {
      basePath == null -> path
      path == basePath -> ""
      path.startsWith("$basePath/") -> path.substring(basePath.length + 1)
      else -> return false // base URI doesn't match this path
    }

    if (isDirectory) {
      // Any directory may contain files that match the glob pattern.
      // Per-file events are not generated, so we need to inform the server about all directory events.
      return true
    }

    val pathMatcher = patternToPathMatcherCache.getOrPut(globPattern) { getPathMatcher(globPattern) }
    return pathMatcher.matches(Path.of(pathToMatch))
  }
}
