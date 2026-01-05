// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.bazelEnvironment

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.useLines
import kotlin.math.max

object BazelRunfilesManifest {
  // https://fuchsia.googlesource.com/fuchsia/+/HEAD/build/bazel/BAZEL_RUNFILES.md?format%2F%2F#how-runfiles-libraries-really-work
  private const val RUNFILES_MANIFEST_FILE_ENV_NAME = "RUNFILES_MANIFEST_FILE"

  val exists: Boolean by lazy {
    val env = System.getenv(RUNFILES_MANIFEST_FILE_ENV_NAME) ?: return@lazy false
    Path.of(env).exists()
  }

  private val bazelRunFilesManifest: Map<String, String> by lazy {
    val file = Path.of(System.getenv(RUNFILES_MANIFEST_FILE_ENV_NAME))
    require(file.exists()) { "RUNFILES_MANIFEST_FILE does not exist: $file" }
    file.useLines { lines ->
      lines
        .filter { it.isNotBlank() && it.isNotEmpty() }
        .map { parseManifestEntry(it) }
        .distinct()
        .toMap()
    }
  }

  private val calculatedManifestEntries: MutableMap<String, String> = mutableMapOf()

  private fun parseManifestEntry(line: String): Pair<String, String> {
    val parts = line.split(" ", limit = 2)
    require(parts.size == 2) { "runfiles_manifest line must have exactly 2 space-separated values: '$line'" }
    return parts[0] to parts[1]
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
      .map { it.trim('/') }
      .map { p -> if (p.isEmpty()) emptyList() else p.split('/').filter { it.isNotEmpty() } }

    if (partsList.isEmpty()) return ""

    // Find the shortest path length to limit comparisons
    val minLength = partsList.minOf { it.size }

    val sb = StringBuilder()
    for (i in 0 until minLength) {
      val segment = partsList[0][i]
      if (partsList.any { it[i] != segment }) break
      if (sb.isNotEmpty()) sb.append('/')
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