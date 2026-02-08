// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.Hashing
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.StripedMutex
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.createSourceAndCacheStrategyList
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private val keyLocks = StripedMutex(256)
private val lockSlotGuards = StripedMutex(256)
// Bump this version when build scripts semantics affecting cache contents change.
private const val CACHE_VERSION = 0

class LocalDiskJarCacheManager(
  cacheDir: Path,
  private val productionClassOutDir: Path,
  private val maxAccessTimeAge: Duration = 3.days,
  metadataTouchInterval: Duration = metadataTouchMinInterval,
  scope: CoroutineScope? = null,
) : JarCacheManager {
  private val versionedCacheDir = cacheDir.resolve("v$CACHE_VERSION")
  private val entriesDir = versionedCacheDir.resolve(entriesDirName)
  private val stripedLockFile = getStripedLockFile(versionedCacheDir)
  private val scopedStripedLockChannel: FileChannel?
  private val lastCleanupMarkerFile = versionedCacheDir.resolve(cleanupMarkerFileName)
  private val legacyPurgeMarkerFile = cacheDir.resolve("$legacyPurgeMarkerPrefix$CACHE_VERSION")
  private val tempFilePrefix = longToString(ProcessHandle.current().pid())
  private val metadataTouchTracker = MetadataTouchTracker(minTouchIntervalMs = metadataTouchInterval.inWholeMilliseconds)
  private val cleanupCandidateIndex = CleanupCandidateIndex()

  init {
    Files.createDirectories(cacheDir)
    Files.createDirectories(versionedCacheDir)
    Files.createDirectories(entriesDir)
    val scopeJob = scope?.coroutineContext?.get(Job)
    if (scopeJob == null) {
      scopedStripedLockChannel = null
    }
    else {
      val channel = FileChannel.open(stripedLockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
      scopedStripedLockChannel = channel
      scopeJob.invokeOnCompletion {
        try {
          channel.close()
        }
        catch (_: Exception) {
        }
      }
    }
    purgeLegacyCacheIfRequired(
      cacheDir = cacheDir,
      versionedCacheDir = versionedCacheDir,
      legacyPurgeMarkerFile = legacyPurgeMarkerFile,
    )
  }

  override suspend fun cleanup() {
    cleanupLocalDiskJarCache(
      entriesDir = entriesDir,
      lastCleanupMarkerFile = lastCleanupMarkerFile,
      maxAccessTimeAge = maxAccessTimeAge,
      cleanupCandidateIndex = cleanupCandidateIndex,
      withCacheEntryLock = { lockSlot, task -> withCacheEntryLock(lockSlot, task) },
    )
  }

  override suspend fun computeIfAbsent(
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ): Path {
    val items = createSourceAndCacheStrategyList(sources = sources, productionClassOutDir = productionClassOutDir)
    val targetFileName = targetFile.fileName?.toString() ?: targetFile.toString()
    val hash = Hashing.xxh3_128().hashStream()
    for (source in items) {
      source.updateAssetDigest(hash)
    }
    hash.putInt(items.size)
    hash.putInt(CACHE_VERSION)
    hash.putString(targetFileName)
    producer.updateDigest(hash)
    val hashValue128 = hash.get()
    val leastSignificantBits = hashValue128.leastSignificantBits
    val key = "${longToString(leastSignificantBits)}-${longToString(hashValue128.mostSignificantBits)}"
    val lockSlot = getLockSlot(leastSignificantBits)
    val paths = getCacheEntryPaths(entriesDir = entriesDir, key = key, targetFileName = targetFileName)

    val optimisticCacheResult = tryUseCacheEntry(
      key = key,
      paths = paths,
      targetFile = targetFile,
      sources = sources,
      items = items,
      nativeFiles = nativeFiles,
      span = span,
      producer = producer,
      metadataTouchTracker = metadataTouchTracker,
      cleanupCandidateIndex = cleanupCandidateIndex,
      deleteInvalidEntry = false,
      failOnCacheIoErrors = false,
    )
    if (optimisticCacheResult != null) {
      return optimisticCacheResult
    }

    return withCacheEntryLock(lockSlot = lockSlot) {
      tryUseCacheEntry(
        key = key,
        paths = paths,
        targetFile = targetFile,
        sources = sources,
        items = items,
        nativeFiles = nativeFiles,
        span = span,
        producer = producer,
        metadataTouchTracker = metadataTouchTracker,
        cleanupCandidateIndex = cleanupCandidateIndex,
        deleteInvalidEntry = true,
        failOnCacheIoErrors = true,
      ) ?: produceAndCache(
        paths = paths,
        producer = producer,
        targetFile = targetFile,
        items = items,
        nativeFiles = nativeFiles,
        tempFilePrefix = tempFilePrefix,
        metadataTouchTracker = metadataTouchTracker,
        cleanupCandidateIndex = cleanupCandidateIndex,
      )
    }
  }

  @Suppress("ConvertTryFinallyToUseCall")
  private suspend fun <T> withCacheEntryLock(lockSlot: Long, task: suspend () -> T): T {
    return keyLocks.getLockByHash(lockSlot).withLock {
      lockSlotGuards.getLockByHash(lockSlot).withLock {
        withContext(Dispatchers.IO) {
          val channel = scopedStripedLockChannel
          if (channel == null) {
            val temporaryChannel = FileChannel.open(stripedLockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            try {
              withFileLock(channel = temporaryChannel, lockSlot = lockSlot, task = task)
            }
            finally {
              temporaryChannel.close()
            }
          }
          else {
            withFileLock(channel = channel, lockSlot = lockSlot, task = task)
          }
        }
      }
    }
  }
}

@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun <T> withFileLock(channel: FileChannel, lockSlot: Long, task: suspend () -> T): T {
  val fileLock = channel.lock(lockSlot, 1, false)
  try {
    return task()
  }
  finally {
    fileLock.release()
  }
}
