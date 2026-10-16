package com.intellij.platform.lsp.impl

import com.intellij.openapi.util.getPathMatcher
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationPatternKind
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class LspGlobMatcher {
  private val patternToPathMatcherCache: MutableMap<String, PathMatcher> = ConcurrentHashMap()

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

  /**
   * @param path file path as seen by server; to get a file path, use [com.intellij.platform.lsp.api.LspClientDescriptor.getFilePath]
   */
  fun pathMatchesFileOperationFilter(
    path: String,
    uriScheme: String,
    isDirectory: Boolean,
    filter: FileOperationFilter,
  ): Boolean {
    if (filter.scheme != null && !filter.scheme.equals(uriScheme, ignoreCase = true)) return false

    val pattern = filter.pattern ?: return false
    when (pattern.matches) {
      null -> {}
      FileOperationPatternKind.File -> if (isDirectory) return false
      FileOperationPatternKind.Folder -> if (!isDirectory) return false
      else -> return false
    }

    val globPattern = pattern.glob ?: return false
    val ignoreCase = pattern.options?.ignoreCase == true
    val glob = if (ignoreCase) globPattern.lowercase(Locale.ROOT) else globPattern
    val pathToMatch = if (ignoreCase) path.lowercase(Locale.ROOT) else path
    val pathMatcher = patternToPathMatcherCache.getOrPut(glob) { getPathMatcher(glob) }
    return try {
      pathMatcher.matches(Path.of(pathToMatch))
    }
    catch (_: InvalidPathException) {
      false
    }
  }
}
