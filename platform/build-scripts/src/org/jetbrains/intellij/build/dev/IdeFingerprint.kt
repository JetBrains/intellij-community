// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val IDE_FINGERPRINT_VERSION = "v3"

private val ideFingerprintEntryComparator = Comparator<IdeFingerprintEntry> { first, second ->
  var result = first.relativePath.compareTo(second.relativePath)
  if (result == 0) {
    result = first.type.compareTo(second.type)
  }
  if (result == 0) {
    result = first.hash.compareTo(second.hash)
  }
  result
}

internal class IdeFingerprintEntry(
  @JvmField val relativePath: String,
  @JvmField val type: String,
  @JvmField val hash: Long,
)

internal suspend fun writeIdeFingerprint(
  entries: Sequence<DistributionFileEntry>,
  runDir: Path,
  projectDir: Path,
) {
  val debug = if (System.getProperty("intellij.build.fingerprint.debug").toBoolean()) StringBuilder() else null
  val fingerprint = computeIdeFingerprint(entries = entries, runDir = runDir, projectDir = projectDir, debug = debug)
  withContext(Dispatchers.IO) {
    Files.writeString(runDir.resolve("fingerprint.txt"), fingerprint)
    debug?.let { Files.writeString(runDir.resolve("fingerprint-debug.txt"), it) }
  }
  Span.current().addEvent("IDE fingerprint: $fingerprint")
}

/**
 * Hashes the identity of every distribution entry, not merely its bytes. Paths and types are part of the input so a
 * move, rename, or change in packaging semantics invalidates a running IDE even when the payload itself is unchanged.
 */
@VisibleForTesting
internal fun computeIdeFingerprint(
  entries: Sequence<DistributionFileEntry>,
  runDir: Path,
  projectDir: Path,
  debug: StringBuilder? = null,
): String {
  val normalizedRunDir = runDir.toAbsolutePath().normalize()
  val normalizedProjectDir = projectDir.toAbsolutePath().normalize()
  val fingerprintEntries = entries.map { entry ->
    val relativePath = getRelativeDistributionPath(entry, normalizedRunDir, normalizedProjectDir)
    IdeFingerprintEntry(
      relativePath = relativePath.invariantSeparatorsPathString,
      type = entry.type,
      hash = entry.hash,
    )
  }.toList()
  return computeIdeFingerprint(fingerprintEntries, debug)
}

internal fun getRelativeDistributionPath(entry: DistributionFileEntry, distributionRoot: Path, projectDir: Path): Path {
  val path = entry.distributionPath.toAbsolutePath().normalize()
  return when {
    path.startsWith(distributionRoot) -> distributionRoot.relativize(path)
    path.startsWith(projectDir) -> projectDir.relativize(path)
    else -> error("Distribution entry is outside the distribution and project roots: ${entry.distributionPath}")
  }
}

@VisibleForTesting
internal fun computeIdeFingerprint(entries: List<IdeFingerprintEntry>, debug: StringBuilder? = null): String {
  val sortedEntries = ArrayList(entries)
  sortedEntries.sortWith(ideFingerprintEntryComparator)
  val hasher = Hashing.xxh3_64().hashStream()
  hasher.putString(IDE_FINGERPRINT_VERSION)
  hasher.putInt(sortedEntries.size)
  debug?.append(IDE_FINGERPRINT_VERSION)?.append(' ')?.append(sortedEntries.size)?.append('\n')
  for (entry in sortedEntries) {
    hasher.putString(entry.relativePath)
    hasher.putString(entry.type)
    hasher.putLong(entry.hash)
    debug?.append(java.lang.Long.toUnsignedString(entry.hash, Character.MAX_RADIX))
      ?.append(' ')?.append(entry.type)?.append(' ')?.append(entry.relativePath)?.append('\n')
  }
  return "$IDE_FINGERPRINT_VERSION:${java.lang.Long.toUnsignedString(hasher.asLong, Character.MAX_RADIX)}"
}
