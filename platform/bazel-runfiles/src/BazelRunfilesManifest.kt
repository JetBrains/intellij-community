// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bazel.runfiles

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.useLines
import kotlin.math.max

@ApiStatus.Internal
class BazelRunfilesManifest @JvmOverloads constructor(
  val manifestFile: String? = System.getenv(RUNFILES_MANIFEST_FILE_ENV_NAME),
) {
  private companion object {
    // https://fuchsia.googlesource.com/fuchsia/+/HEAD/build/bazel/BAZEL_RUNFILES.md?format%2F%2F#how-runfiles-libraries-really-work
    const val RUNFILES_MANIFEST_FILE_ENV_NAME: String = "RUNFILES_MANIFEST_FILE"
  }

  override fun toString(): String {
    return "BazelRunfilesManifest(manifestFile=$manifestFile)"
  }

  val exists: Boolean by lazy {
    manifestFile ?: return@lazy false
    Path.of(manifestFile).exists()
  }

  val manifest: Path by lazy {
    require(manifestFile != null && exists) { "RUNFILES_MANIFEST_FILE is not set or does not exist: $manifestFile" }
    Path.of(manifestFile)
  }

  private val bazelRunFilesManifest: Map<String, String> by lazy {
    manifest.useLines { lines ->
      lines
        .filter { it.isNotBlank() && it.isNotEmpty() }
        .map { parseManifestEntry(it) }
        .distinct()
        .toMap()
    }
  }

  /** Parsed manifest entries as rlocation-path -> real-path. */
  val entries: Map<String, String> get() = bazelRunFilesManifest

  private val calculatedManifestEntries: MutableMap<String, String> = mutableMapOf()

  private fun parseManifestEntry(line: String): Pair<String, String> {
    if (line.startsWith(" ")) {
      val separatorIndex = line.indexOf(' ', startIndex = 1)
      require(separatorIndex > 1) { "escaped runfiles_manifest line must have exactly 2 space-separated values: '$line'" }
      return unescapeManifestPath(line.substring(1, separatorIndex)) to line.substring(separatorIndex + 1)
    }

    val parts = line.split(" ", limit = 2)
    require(parts.size == 2) { "runfiles_manifest line must have exactly 2 space-separated values: '$line'" }
    return parts[0] to parts[1]
  }

  /**
   * escaped path if there are whitespace symbols in the path, see
   * https://github.com/bazelbuild/bazel/blob/5b6147736c14d1804df8ab1b2ff544e060465dd3/src/main/java/com/google/devtools/build/lib/analysis/SourceManifestAction.java#L363
   * If rootRelativePath contains spaces, then each backslash is replaced with '\b', each space
   * is replaced with '\s' and the line is prefixed with a space.
   */
  private fun unescapeManifestPath(path: String): String {
    if ('\\' !in path) {
      return path
    }

    return buildString(path.length) {
      var index = 0
      while (index < path.length) {
        val char = path[index++]
        if (char != '\\') {
          append(char)
          continue
        }

        require(index < path.length) { "Unterminated escape sequence in runfiles_manifest path: '$path'" }
        append(when (val escapedChar = path[index++]) {
          's' -> ' '
          'n' -> '\n'
          'b' -> '\\'
          else -> error("Unexpected escape sequence in runfiles_manifest path '$path': \\$escapedChar")
        })
      }
    }
  }

  fun get(key: String) : String {
    val valueByFullKey = bazelRunFilesManifest[key]
    if (valueByFullKey != null) {
      return valueByFullKey
    }

    // required to resolve directories as in the manifest entries
    // only files are present as keys, but not directories
    return calculatedManifestEntries.computeIfAbsent(key) {
      val subset = bazelRunFilesManifest.filter { it.key.startsWith(key) }
      val calculatedValue = if (subset.isNotEmpty()) {
        val longestKey = findLongestCommonPrefix(subset.keys)
        val longestValue = findLongestCommonPrefix(subset.values.toSet())
        mapByQuery(longestKey, longestValue, key)
      }
      else {
        key
      }

      return@computeIfAbsent calculatedValue
    }
  }


  private fun findLongestCommonPrefix(paths: Set<String>?): String {
    // Handle null/empty gracefully
    if (paths.isNullOrEmpty()) return ""

    // Normalize and split into path segments without using regex; ignore empty segments
    val partsList: List<List<String>> = paths
      .map { p -> if (p.isEmpty()) emptyList() else p.split('/') }

    if (partsList.isEmpty()) return ""

    // Find the shortest path length to limit comparisons
    val minLength = partsList.minOf { it.size }

    val sb = StringBuilder()
    for (i in 0 until minLength) {
      val segment = partsList[0][i]
      if (partsList.any { it[i] != segment }) break
      if (i > 0) sb.append('/')
      sb.append(segment)
    }
    return sb.toString()
  }

  private fun mapByQuery(fullKey: String, value: String, queryKey: String): String {
    require(fullKey.isNotEmpty() && value.isNotEmpty() && queryKey.isNotEmpty()) { "Query cannot be empty" }

    val fullKeyParts: Array<String> = fullKey.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
    val valueParts: Array<String> = value.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
    val queryKeyParts: Array<String?> = queryKey.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()

    require(fullKeyParts.size >= queryKeyParts.size) { "Query key $queryKey is longer than full key $fullKey" }

    val removeCount = fullKeyParts.size - queryKeyParts.size
    val endIndex = max(valueParts.size - removeCount, 0)

    return buildString {
      for (i in 0..<endIndex) {
        if (i > 0) {
          append('/')
        }
        append(valueParts[i])
      }
    }
  }
}
