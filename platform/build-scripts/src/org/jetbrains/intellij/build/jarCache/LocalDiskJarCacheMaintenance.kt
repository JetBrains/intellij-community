// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.ArrayDeque
import kotlin.random.Random
import kotlin.time.Duration

private const val cleanupCandidateQueueMaxSize = 200_000
private const val cleanupCandidateBatchLimit = 50_000
private const val cleanupReservedScanCandidates = cleanupCandidateBatchLimit / 10
private const val cleanupScanCursorSeparator = '|'
private const val maxVersionsPerTarget = 3
private const val recentTouchFailurePriorityAccessTime = Long.MAX_VALUE

internal data class CleanupCandidate(
  @JvmField val entryStem: String,
  @JvmField val shardDirName: String,
) {
  @JvmField
  val dedupKey: String = "$shardDirName|$entryStem"
}

private data class CleanupScanCursor(
  @JvmField val shardDirName: String,
  @JvmField val entryStemExclusive: String?,
)

private data class EntryStemBatch(
  @JvmField val entryStems: List<String>,
  @JvmField val exhausted: Boolean,
)

private data class TargetRetentionCandidate(
  @JvmField val cleanupCandidate: CleanupCandidate,
  @JvmField val entryStem: String,
  @JvmField val metadataAccessTime: Long,
)

internal class CleanupCandidateIndex(
  private val maxEntries: Int = cleanupCandidateQueueMaxSize,
) {
  private val lock = Any()
  private val queue = ArrayDeque<CleanupCandidate>()
  private val queuedEntries = HashSet<String>()

  init {
    check(maxEntries > 0) { "Expected positive maxEntries, but got $maxEntries" }
  }

  fun register(entryStem: String, shardDirName: String) {
    if (!entryStem.contains(entryNameSeparator)) {
      return
    }

    val candidate = CleanupCandidate(entryStem = entryStem, shardDirName = shardDirName)

    synchronized(lock) {
      if (!queuedEntries.add(candidate.dedupKey)) {
        return
      }

      if (queue.size >= maxEntries) {
        val evicted = queue.removeFirst()
        queuedEntries.remove(evicted.dedupKey)
      }

      queue.addLast(candidate)
    }
  }

  fun drain(limit: Int): List<CleanupCandidate> {
    if (limit <= 0) {
      return emptyList()
    }

    synchronized(lock) {
      if (queue.isEmpty()) {
        return emptyList()
      }

      val count = minOf(limit, queue.size)
      val result = ArrayList<CleanupCandidate>(count)
      repeat(count) {
        val candidate = queue.removeFirst()
        queuedEntries.remove(candidate.dedupKey)
        result.add(candidate)
      }
      return result
    }
  }
}

internal suspend fun cleanupLocalDiskJarCache(
  entriesDir: Path,
  lastCleanupMarkerFile: Path,
  cleanupInterval: Duration,
  maxAccessTimeAge: Duration,
  cleanupCandidateIndex: CleanupCandidateIndex,
  metadataTouchTracker: MetadataTouchTracker,
  withCacheEntryLock: suspend (Long, suspend () -> Unit) -> Unit,
) {
  withContext(Dispatchers.IO) {
    try {
      if (!isTimeForCleanup(lastCleanupMarkerFile = lastCleanupMarkerFile, cleanupInterval = cleanupInterval)) {
        return@withContext
      }

      cleanupEntries(
        entriesDir = entriesDir,
        currentTime = System.currentTimeMillis(),
        maxTimeMs = maxAccessTimeAge.inWholeMilliseconds,
        cleanupCandidateIndex = cleanupCandidateIndex,
        metadataTouchTracker = metadataTouchTracker,
        withCacheEntryLock = withCacheEntryLock,
      )
      Files.writeString(lastCleanupMarkerFile, LocalDateTime.now().toString())
    }
    catch (_: IOException) {
      // cleanup is best-effort
    }
  }
}

internal fun purgeLegacyCacheIfRequired(
  cacheDir: Path,
  versionedCacheDir: Path,
  legacyPurgeMarkerFile: Path,
) {
  if (Files.exists(legacyPurgeMarkerFile)) {
    return
  }

  try {
    Files.newDirectoryStream(cacheDir).use { stream ->
      for (entry in stream) {
        if (entry == versionedCacheDir || entry == legacyPurgeMarkerFile) {
          continue
        }

        if (Files.isDirectory(entry)) {
          if (isLegacyVersionDirectory(entry.fileName.toString())) {
            deletePathRecursively(entry)
          }
          continue
        }

        if (!Files.isRegularFile(entry)) {
          continue
        }

        if (!isLegacyFlatMetadataFile(entry.fileName.toString())) {
          continue
        }

        val jarFile = entry.resolveSibling(entry.fileName.toString().removeSuffix(legacyMetadataSuffix) + legacyJarSuffix)
        Files.deleteIfExists(entry)
        Files.deleteIfExists(jarFile)

        val metadataMarkFile = entry.resolveSibling(entry.fileName.toString() + markedForCleanupFileSuffix)
        val jarMarkFile = jarFile.resolveSibling(jarFile.fileName.toString() + markedForCleanupFileSuffix)
        Files.deleteIfExists(metadataMarkFile)
        Files.deleteIfExists(jarMarkFile)
      }
    }

    Files.deleteIfExists(cacheDir.resolve(cleanupMarkerFileName))

    Files.writeString(legacyPurgeMarkerFile, LocalDateTime.now().toString())
  }
  catch (_: IOException) {
    // legacy purge is best-effort
  }
}

private fun isTimeForCleanup(lastCleanupMarkerFile: Path, cleanupInterval: Duration): Boolean {
  if (Files.notExists(lastCleanupMarkerFile)) {
    return true
  }

  val lastCleanupTime = try {
    Files.getLastModifiedTime(lastCleanupMarkerFile).toMillis()
  }
  catch (_: NoSuchFileException) {
    return true
  }
  catch (_: IOException) {
    return true
  }

  return lastCleanupTime < (System.currentTimeMillis() - cleanupInterval.inWholeMilliseconds)
}

private suspend fun cleanupEntries(
  entriesDir: Path,
  currentTime: Long,
  maxTimeMs: Long,
  cleanupCandidateIndex: CleanupCandidateIndex,
  metadataTouchTracker: MetadataTouchTracker,
  withCacheEntryLock: suspend (Long, suspend () -> Unit) -> Unit,
) {
  withContext(Dispatchers.IO) {
    val staleThreshold = currentTime - maxTimeMs
    val scanCursorFile = entriesDir.resolveSibling(cleanupScanCursorFileName)
    val candidates = collectCandidatesForCleanup(
      entriesDir = entriesDir,
      scanCursorFile = scanCursorFile,
      cleanupCandidateIndex = cleanupCandidateIndex,
    )
    val overflowCandidates = collectOverflowCandidatesByTarget(
      entriesDir = entriesDir,
      candidates = candidates,
      currentTime = currentTime,
      metadataTouchTracker = metadataTouchTracker,
    )
    for (candidate in candidates) {
      val entryStem = candidate.entryStem
      val key = getCacheKeyFromEntryStem(entryStem)
      val entryShardDir = entriesDir.resolve(candidate.shardDirName)
      val paths = getCacheEntryPathsByStem(entryShardDir = entryShardDir, entryStem = entryStem)

      if (key == null) {
        deleteEntryFiles(paths)
        continue
      }

      // Cleanup sees only persisted entry names. Recover the lsb from stored key prefix
      // ("<lsb>-<msb>") and feed it into StripedMutex.getLockByHash.
      val lockHash = parseLeastSignificantBitsFromKey(key)
      if (lockHash == null) {
        // Entries with malformed key prefix are unreachable by computeIfAbsent, so remove
        // them directly instead of keeping garbage forever.
        deleteEntryFiles(paths)
        continue
      }

      val isOverflowCandidate = overflowCandidates.contains(candidate.dedupKey)
      if (!isOverflowCandidate && !shouldInspectEntryUnderLock(paths = paths, staleThreshold = staleThreshold)) {
        continue
      }

      withCacheEntryLock(lockHash) {
        if (isOverflowCandidate) {
          deleteEntryFiles(paths)
          return@withCacheEntryLock
        }

        cleanupEntry(
          paths = paths,
          staleThreshold = staleThreshold,
          currentTime = currentTime,
          metadataTouchTracker = metadataTouchTracker,
        )
      }
    }
  }
}

private fun collectOverflowCandidatesByTarget(
  entriesDir: Path,
  candidates: List<CleanupCandidate>,
  currentTime: Long,
  metadataTouchTracker: MetadataTouchTracker,
): Set<String> {
  if (candidates.isEmpty()) {
    return emptySet()
  }

  val byTargetName = HashMap<String, MutableList<TargetRetentionCandidate>>()
  for (candidate in candidates) {
    val entryStem = candidate.entryStem
    val targetFileName = getTargetNameFromEntryStem(entryStem) ?: continue
    val metadataAccessTime = if (metadataTouchTracker.hasRecentTouchFailure(entryStem, currentTime)) {
      // Honor touch-failure grace: treat entry as freshest while grace is active.
      recentTouchFailurePriorityAccessTime
    }
    else {
      val metadataFile = entriesDir.resolve(candidate.shardDirName).resolve(entryStem + metadataFileSuffix)
      readMetadataAccessTimeOrOldest(metadataFile)
    }
    byTargetName.computeIfAbsent(targetFileName) { mutableListOf() }
      .add(
        TargetRetentionCandidate(
          cleanupCandidate = candidate,
          entryStem = entryStem,
          metadataAccessTime = metadataAccessTime,
        ),
      )
  }

  val overflow = HashSet<String>()
  for (targetCandidates in byTargetName.values) {
    if (targetCandidates.size <= maxVersionsPerTarget) {
      continue
    }

    targetCandidates.sortWith(
      compareByDescending<TargetRetentionCandidate> { it.metadataAccessTime }
        .thenByDescending { it.entryStem }
        .thenByDescending { it.cleanupCandidate.shardDirName },
    )
    for (index in maxVersionsPerTarget until targetCandidates.size) {
      overflow.add(targetCandidates[index].cleanupCandidate.dedupKey)
    }
  }
  return overflow
}

private fun readMetadataAccessTimeOrOldest(metadataFile: Path): Long {
  return try {
    Files.getLastModifiedTime(metadataFile).toMillis()
  }
  catch (_: NoSuchFileException) {
    Long.MIN_VALUE
  }
  catch (_: IOException) {
    Long.MIN_VALUE
  }
}

private fun collectCandidatesForCleanup(
  entriesDir: Path,
  scanCursorFile: Path,
  cleanupCandidateIndex: CleanupCandidateIndex,
): List<CleanupCandidate> {
  // See README.md: "Cleanup Candidate Queue Plus Reserved Scan".
  // Always reserve scan capacity so cold/misplaced entries are eventually discovered,
  // then continue scanning until this cleanup run reaches the batch cap or exhausts shards.
  val reservedScanCount = cleanupReservedScanCandidates.coerceIn(1, cleanupCandidateBatchLimit)
  val queueDrainLimit = (cleanupCandidateBatchLimit - reservedScanCount).coerceAtLeast(0)
  val result = LinkedHashMap<String, CleanupCandidate>(cleanupCandidateBatchLimit)
  for (candidate in cleanupCandidateIndex.drain(queueDrainLimit)) {
    result.putIfAbsent(candidate.dedupKey, candidate)
  }

  val remaining = cleanupCandidateBatchLimit - result.size
  if (remaining > 0) {
    for (candidate in collectEntryStemsFromShardWindow(
      entriesDir = entriesDir,
      scanCursorFile = scanCursorFile,
      maxEntries = remaining,
    )) {
      result.putIfAbsent(candidate.dedupKey, candidate)
    }
  }

  return result.values.toList()
}

private fun collectEntryStemsFromShardWindow(
  entriesDir: Path,
  scanCursorFile: Path,
  maxEntries: Int,
): List<CleanupCandidate> {
  if (maxEntries <= 0) {
    return emptyList()
  }

  val shardDirs = listShardDirectories(entriesDir)
  if (shardDirs.isEmpty()) {
    return emptyList()
  }

  val scanCursor = readCleanupScanCursor(scanCursorFile = scanCursorFile, shardDirs = shardDirs)
  val startIndex = shardDirs.indexOfFirst { it.fileName.toString() == scanCursor.shardDirName }.let { if (it >= 0) it else 0 }
  val result = LinkedHashMap<String, CleanupCandidate>(maxEntries)
  var index = startIndex
  var scannedShards = 0
  var nextCursor: CleanupScanCursor? = null
  while (scannedShards < shardDirs.size && result.size < maxEntries) {
    val shardDir = shardDirs[index]
    val remaining = maxEntries - result.size
    val shardDirName = shardDir.fileName.toString()
    val startAfterExclusive = if (scannedShards == 0 && shardDirName == scanCursor.shardDirName) scanCursor.entryStemExclusive else null
    val batch = collectEntryStems(shardDir = shardDir, maxEntries = remaining, startAfterExclusive = startAfterExclusive)
    for (entryStem in batch.entryStems) {
      val candidate = CleanupCandidate(entryStem = entryStem, shardDirName = shardDirName)
      result.putIfAbsent(candidate.dedupKey, candidate)
      if (result.size >= maxEntries) {
        break
      }
    }

    if (!batch.exhausted) {
      nextCursor = CleanupScanCursor(shardDirName = shardDirName, entryStemExclusive = batch.entryStems.lastOrNull())
      break
    }

    scannedShards++
    index = (index + 1) % shardDirs.size
  }

  if (nextCursor == null) {
    nextCursor = CleanupScanCursor(shardDirName = shardDirs[index].fileName.toString(), entryStemExclusive = null)
  }
  writeCleanupScanCursor(scanCursorFile = scanCursorFile, cursor = nextCursor)
  return result.values.toList()
}

private fun listShardDirectories(entriesDir: Path): List<Path> {
  val shardDirs = try {
    Files.newDirectoryStream(entriesDir)
  }
  catch (_: NoSuchFileException) {
    return emptyList()
  }

  shardDirs.use { stream ->
    val result = mutableListOf<Path>()
    for (entry in stream) {
      if (Files.isDirectory(entry)) {
        result.add(entry)
      }
    }
    result.sortBy { it.fileName.toString() }
    return result
  }
}

private fun readCleanupScanCursor(scanCursorFile: Path, shardDirs: List<Path>): CleanupScanCursor {
  val defaultCursor = CleanupScanCursor(shardDirName = shardDirs.first().fileName.toString(), entryStemExclusive = null)
  return try {
    if (Files.notExists(scanCursorFile)) {
      defaultCursor
    }
    else {
      val cursorValue = Files.readString(scanCursorFile).trim()
      if (cursorValue.isEmpty()) {
        defaultCursor
      }
      else {
        val separatorIndex = cursorValue.indexOf(cleanupScanCursorSeparator)
        val shardDirName = (if (separatorIndex >= 0) cursorValue.substring(0, separatorIndex) else cursorValue).ifEmpty {
          defaultCursor.shardDirName
        }
        val entryStemExclusive = if (separatorIndex >= 0) {
          cursorValue.substring(separatorIndex + 1).ifEmpty { null }
        }
        else {
          null
        }
        val matchedShardDirName = shardDirs.firstOrNull { it.fileName.toString() == shardDirName }?.fileName?.toString()
        if (matchedShardDirName == null) {
          defaultCursor
        }
        else {
          CleanupScanCursor(
            shardDirName = matchedShardDirName,
            entryStemExclusive = if (matchedShardDirName == shardDirName) entryStemExclusive else null,
          )
        }
      }
    }
  }
  catch (_: IOException) {
    defaultCursor
  }
}

private fun writeCleanupScanCursor(scanCursorFile: Path, cursor: CleanupScanCursor) {
  val serializedCursor = buildString {
    append(cursor.shardDirName)
    cursor.entryStemExclusive?.let {
      append(cleanupScanCursorSeparator)
      append(it)
    }
  }

  try {
    writeCleanupScanCursorAtomically(
      scanCursorFile,
      serializedCursor,
    )
  }
  catch (_: IOException) {
    // cleanup is best-effort
  }
}

internal fun writeCleanupScanCursorAtomically(
  scanCursorFile: Path,
  serializedCursor: String,
  tempFilePrefix: String = "cleanup-scan-cursor",
  moveFile: (Path, Path) -> Unit = ::moveReplacing,
) {
  val tempFileName = buildTempSiblingFileName(
    baseFileName = scanCursorFile.fileName.toString(),
    tempFilePrefix = tempFilePrefix,
    randomSuffix = Random.nextLong(),
  )
  val tempFile = scanCursorFile.resolveSibling(tempFileName)
  var published = false
  try {
    Files.writeString(
      tempFile,
      serializedCursor,
      StandardOpenOption.CREATE_NEW,
      StandardOpenOption.WRITE,
    )
    moveFile(tempFile, scanCursorFile)
    published = true
  }
  finally {
    if (!published) {
      try {
        Files.deleteIfExists(tempFile)
      }
      catch (_: IOException) {
        // cleanup is best-effort
      }
    }
  }
}

private fun collectEntryStems(
  shardDir: Path,
  maxEntries: Int = Int.MAX_VALUE,
  startAfterExclusive: String? = null,
): EntryStemBatch {
  if (maxEntries <= 0) {
    return EntryStemBatch(entryStems = emptyList(), exhausted = true)
  }

  val stems = java.util.TreeSet<String>()
  try {
    Files.newDirectoryStream(shardDir).use { files ->
      for (file in files) {
        if (!Files.isRegularFile(file)) {
          continue
        }

        val fileName = file.fileName.toString()
        val stem = when {
          fileName.endsWith(metadataFileSuffix) -> fileName.removeSuffix(metadataFileSuffix)
          fileName.endsWith(markedForCleanupFileSuffix) -> fileName.removeSuffix(markedForCleanupFileSuffix)
          else -> fileName
        }
        if (!stem.contains(entryNameSeparator)) {
          continue
        }
        stems.add(stem)
      }
    }
  }
  catch (_: IOException) {
    // cleanup is best-effort
  }

  if (stems.isEmpty()) {
    return EntryStemBatch(entryStems = emptyList(), exhausted = true)
  }

  val tailStems = if (startAfterExclusive == null) stems else stems.tailSet(startAfterExclusive, false)
  if (tailStems.isEmpty()) {
    return EntryStemBatch(entryStems = emptyList(), exhausted = true)
  }

  val entryStems = ArrayList<String>(minOf(maxEntries, tailStems.size))
  for (entryStem in tailStems) {
    entryStems.add(entryStem)
    if (entryStems.size >= maxEntries) {
      break
    }
  }

  return EntryStemBatch(entryStems = entryStems, exhausted = tailStems.size <= entryStems.size)
}

private fun shouldInspectEntryUnderLock(paths: CacheEntryPaths, staleThreshold: Long): Boolean {
  val metadataFile = paths.metadataFile
  if (Files.notExists(metadataFile)) {
    return true
  }

  if (Files.notExists(paths.payloadFile)) {
    return true
  }

  // Fresh entries without cleanup mark don't require lock-protected mutation.
  if (Files.notExists(paths.markFile)) {
    val lastAccessTime = try {
      Files.getLastModifiedTime(metadataFile).toMillis()
    }
    catch (_: NoSuchFileException) {
      return true
    }
    catch (_: IOException) {
      return true
    }

    return lastAccessTime <= staleThreshold
  }

  return true
}

private fun cleanupEntry(
  paths: CacheEntryPaths,
  staleThreshold: Long,
  currentTime: Long,
  metadataTouchTracker: MetadataTouchTracker,
) {
  val metadataFile = paths.metadataFile
  if (Files.notExists(metadataFile)) {
    deleteEntryFiles(paths)
    return
  }

  if (Files.notExists(paths.payloadFile)) {
    deleteEntryFiles(paths)
    return
  }

  val lastAccessTime = try {
    Files.getLastModifiedTime(metadataFile).toMillis()
  }
  catch (_: NoSuchFileException) {
    return
  }
  catch (_: IOException) {
    return
  }

  val markFile = paths.markFile
  if (metadataTouchTracker.hasRecentTouchFailure(paths.entryStem, currentTime)) {
    try {
      Files.deleteIfExists(markFile)
    }
    catch (_: IOException) {
      // cleanup is best-effort
    }
    return
  }

  if (lastAccessTime > staleThreshold) {
    try {
      Files.deleteIfExists(markFile)
    }
    catch (_: IOException) {
      // cleanup is best-effort
    }
    return
  }

  if (Files.exists(markFile)) {
    deleteEntryFiles(paths)
  }
  else {
    try {
      Files.newByteChannel(markFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).close()
    }
    catch (_: IOException) {
      // cleanup is best-effort
    }
  }
}

private fun isLegacyFlatMetadataFile(fileName: String): Boolean {
  return fileName.endsWith(legacyMetadataSuffix) && legacyFlatMetadataPattern.matches(fileName)
}

private fun isLegacyVersionDirectory(fileName: String): Boolean {
  return legacyVersionDirectoryPattern.matches(fileName)
}
