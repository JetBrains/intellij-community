// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal const val entriesDirName = "entries"
internal const val metadataFileSuffix = ".meta"
internal const val markedForCleanupFileSuffix = ".mark"
internal const val entryNameSeparator = "__"
internal const val cleanupMarkerFileName = ".last.cleanup.marker"
internal const val cleanupScanCursorFileName = ".cleanup.scan.cursor"
internal const val legacyJarSuffix = ".jar"
internal const val legacyMetadataSuffix = ".m"
internal const val metadataMagic = 0x4A434D31
internal const val metadataSchemaVersion = 2
internal const val legacyPurgeMarkerPrefix = ".legacy-format-purged."
internal val defaultCleanupEveryDuration = 1.days
internal val metadataTouchMinInterval = 15.minutes
internal val legacyFlatMetadataPattern = Regex(".+-\\d+-[0-9a-z]+-[0-9a-z]+\\.m")
internal val legacyVersionDirectoryPattern = Regex("v\\d+")
private const val maxTargetFileNameLengthInEntryName = 80
private const val maxCacheFileNameLength = 255
private val maxEntryStemLength = maxCacheFileNameLength - maxOf(metadataFileSuffix.length, markedForCleanupFileSuffix.length)
private val allowedCacheFileNameChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-".toSet()

internal data class CacheEntryPaths(
  @JvmField val entryShardDir: Path,
  @JvmField val entryStem: String,
  @JvmField val payloadFile: Path,
  @JvmField val metadataFile: Path,
  @JvmField val markFile: Path,
)

internal fun getCacheEntryPaths(entriesDir: Path, key: String, targetFileName: String): CacheEntryPaths {
  val entryStem = buildCacheEntryStem(key = key, targetFileName = targetFileName)
  return getCacheEntryPathsByStem(entryShardDir = entriesDir.resolve(getShard(key)), entryStem = entryStem)
}

internal fun getCacheEntryPathsByStem(entryShardDir: Path, entryStem: String): CacheEntryPaths {
  val payloadFile = entryShardDir.resolve(entryStem)
  return CacheEntryPaths(
    entryShardDir = entryShardDir,
    entryStem = entryStem,
    payloadFile = payloadFile,
    metadataFile = entryShardDir.resolve(entryStem + metadataFileSuffix),
    markFile = entryShardDir.resolve(entryStem + markedForCleanupFileSuffix),
  )
}

internal fun getCacheKeyFromEntryStem(entryStem: String): String? {
  val separatorIndex = entryStem.indexOf(entryNameSeparator)
  if (separatorIndex <= 0) {
    return null
  }
  return entryStem.substring(0, separatorIndex)
}

internal fun getTargetNameFromEntryStem(entryStem: String): String? {
  val separatorIndex = entryStem.indexOf(entryNameSeparator)
  if (separatorIndex <= 0) {
    return null
  }

  val targetNameStartIndex = separatorIndex + entryNameSeparator.length
  if (targetNameStartIndex >= entryStem.length) {
    return null
  }
  return entryStem.substring(targetNameStartIndex)
}

internal fun parseLeastSignificantBitsFromKey(key: String): Long? {
  // Keys are persisted as "<lsb>-<msb>" with unsigned radix-36 numbers.
  // Cleanup does not have original hashValue128, so it must decode the stored lsb prefix
  // and feed it directly into StripedMutex.getLockByHash.
  val separatorIndex = key.indexOf('-')
  if (separatorIndex <= 0) {
    return null
  }

  return key.substring(0, separatorIndex).toULongOrNull(Character.MAX_RADIX)?.toLong()
}

internal fun longToString(v: Long): String = java.lang.Long.toUnsignedString(v, Character.MAX_RADIX)

internal fun getShard(key: String): String = key.take(2).padEnd(2, '0')

internal fun buildCacheEntryStem(key: String, targetFileName: String): String {
  val maxTargetFileNameLengthByFsLimit = maxEntryStemLength - key.length - entryNameSeparator.length
  val maxTargetFileNameLength = minOf(maxTargetFileNameLengthInEntryName, maxTargetFileNameLengthByFsLimit).coerceAtLeast(1)

  val sanitizedTargetFileName = sanitizeTargetFileNameForCache(
    fileName = targetFileName,
    maxLength = maxTargetFileNameLength,
  )
  return "$key$entryNameSeparator$sanitizedTargetFileName"
}

internal fun sanitizeTargetFileNameForCache(fileName: String, maxLength: Int = maxTargetFileNameLengthInEntryName): String {
  check(maxLength > 0) {
    "Expected positive maxLength, but got $maxLength"
  }
  val sanitized = StringBuilder(fileName.length.coerceAtMost(maxLength))
  var previousUnderscore = false
  for (char in fileName) {
    val normalizedChar = if (char in allowedCacheFileNameChars) char else '_'
    if (normalizedChar == '_') {
      if (previousUnderscore) {
        continue
      }
      previousUnderscore = true
    }
    else {
      previousUnderscore = false
    }

    sanitized.append(normalizedChar)
    if (sanitized.length >= maxLength) {
      break
    }
  }

  return sanitized.toString().trim('_').ifEmpty { "u" }
}

internal fun buildTempSiblingFileName(baseFileName: String, tempFilePrefix: String, randomSuffix: Long): String {
  // See README.md: "Temp File Name Cap".
  val suffix = ".tmp.$tempFilePrefix-${longToString(randomSuffix)}"
  val maxBaseNameLength = (maxCacheFileNameLength - suffix.length).coerceAtLeast(1)
  val safeBaseName = if (baseFileName.length <= maxBaseNameLength) baseFileName else baseFileName.take(maxBaseNameLength)
  return safeBaseName + suffix
}

internal fun moveReplacing(from: Path, to: Path) {
  try {
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }
  catch (_: AtomicMoveNotSupportedException) {
    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
  }
}

@OptIn(ExperimentalPathApi::class)
internal fun deletePathRecursively(path: Path) {
  try {
    path.deleteRecursively()
  }
  catch (_: NoSuchFileException) {
  }
  catch (_: IOException) {
  }
}

internal fun deleteEntryFiles(paths: CacheEntryPaths) {
  tryDeleteEntryFile(paths.payloadFile)
  tryDeleteEntryFile(paths.metadataFile)
  tryDeleteEntryFile(paths.markFile)

  val shardDir = paths.entryShardDir
  try {
    Files.deleteIfExists(shardDir)
  }
  catch (_: IOException) {
    // ignore if shard dir is not empty or already removed
  }
}

private fun tryDeleteEntryFile(file: Path) {
  try {
    Files.deleteIfExists(file)
  }
  catch (_: IOException) {
    // best-effort cleanup
  }
}
