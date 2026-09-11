// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.Hashing
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.StripedLock
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.createSourceAndCacheStrategyList
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private val keyLocks = StripedLock(256)
// Bump this version when build scripts semantics affecting cache contents change.
private const val CACHE_VERSION = 1

/**
 * What this call did: `hit`, `hitUnderLock`, or `produced`.
 *
 * An aggregation over a trace groups by this. A hit and a produce cost different things, and a run that reuses
 * everything and a run that packs everything are indistinguishable without it.
 */
private const val JAR_CACHE_OUTCOME = "jarCache.outcome"

/** How long the cache key took to compute. It walks every source of the jar. */
private const val JAR_CACHE_DIGEST_MS = "jarCache.digestMs"

/** How many sources the key covers, which is what the digest walks. */
private const val JAR_CACHE_SOURCE_COUNT = "jarCache.sourceCount"

class LocalDiskJarCacheManager(
  cacheDir: Path,
  private val classesOutputDirectory: Path,
  private val maxAccessTimeAge: Duration = 3.days,
  metadataTouchInterval: Duration = metadataTouchMinInterval,
  private val cleanupInterval: Duration = defaultCleanupEveryDuration,
) : JarCacheManager {
  private val versionedCacheDir = cacheDir.resolve("v$CACHE_VERSION")
  private val entriesDir = versionedCacheDir.resolve(entriesDirName)
  private val lastCleanupMarkerFile = versionedCacheDir.resolve(cleanupMarkerFileName)
  private val legacyPurgeMarkerFile = cacheDir.resolve("$legacyPurgeMarkerPrefix$CACHE_VERSION")
  private val tempFilePrefix = longToString(ProcessHandle.current().pid())
  private val metadataTouchTracker = MetadataTouchTracker(minTouchIntervalMs = metadataTouchInterval.inWholeMilliseconds)
  private val cleanupCandidateIndex = CleanupCandidateIndex()

  init {
    Files.createDirectories(cacheDir)
    Files.createDirectories(versionedCacheDir)
    Files.createDirectories(entriesDir)
    purgeLegacyCacheIfRequired(
      cacheDir = cacheDir,
      versionedCacheDir = versionedCacheDir,
      legacyPurgeMarkerFile = legacyPurgeMarkerFile,
    )
  }

  override fun cleanup() {
    cleanupLocalDiskJarCache(
      entriesDir = entriesDir,
      lastCleanupMarkerFile = lastCleanupMarkerFile,
      cleanupInterval = cleanupInterval,
      maxAccessTimeAge = maxAccessTimeAge,
      cleanupCandidateIndex = cleanupCandidateIndex,
      metadataTouchTracker = metadataTouchTracker,
      withCacheEntryLock = { lockHash, task -> withCacheEntryLock(lockHash, task) },
    )
  }

  override fun computeIfAbsent(
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ) {
    val digestStartNano = System.nanoTime()
    val items = createSourceAndCacheStrategyList(sources = sources, classesOutputDirectory = classesOutputDirectory)
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
    // The outcome and the digest cost are attributes, not spans of their own: this runs once per jar of every
    // distribution, and a trace of one packaging suite already holds more than 14 000 `build jar` spans.
    span.setAttribute(JAR_CACHE_DIGEST_MS, (System.nanoTime() - digestStartNano) / 1_000_000)
    span.setAttribute(JAR_CACHE_SOURCE_COUNT, items.size.toLong())
    val leastSignificantBits = hashValue128.leastSignificantBits
    val key = "${longToString(leastSignificantBits)}-${longToString(hashValue128.mostSignificantBits)}"
    val paths = getCacheEntryPaths(entriesDir = entriesDir, key = key, targetFileName = targetFileName)

    val optimisticHit = tryUseCacheEntry(
      key = key,
      paths = paths,
      targetFile = targetFile,
      items = items,
      nativeFiles = nativeFiles,
      span = span,
      producer = producer,
      metadataTouchTracker = metadataTouchTracker,
      cleanupCandidateIndex = cleanupCandidateIndex,
      underLock = false,
    )
    if (optimisticHit) {
      span.setAttribute(JAR_CACHE_OUTCOME, "hit")
      return
    }

    withCacheEntryLock(lockHash = leastSignificantBits) {
      val hitUnderLock = tryUseCacheEntry(
        key = key,
        paths = paths,
        targetFile = targetFile,
        items = items,
        nativeFiles = nativeFiles,
        span = span,
        producer = producer,
        metadataTouchTracker = metadataTouchTracker,
        cleanupCandidateIndex = cleanupCandidateIndex,
        underLock = true,
      )
      if (hitUnderLock) {
        span.setAttribute(JAR_CACHE_OUTCOME, "hitUnderLock")
      }
      else {
        produceAndCache(
          paths = paths,
          producer = producer,
          targetFile = targetFile,
          items = items,
          nativeFiles = nativeFiles,
          tempFilePrefix = tempFilePrefix,
          metadataTouchTracker = metadataTouchTracker,
          cleanupCandidateIndex = cleanupCandidateIndex,
        )
        span.setAttribute(JAR_CACHE_OUTCOME, "produced")
      }
    }
  }

  private fun <T> withCacheEntryLock(lockHash: Long, task: () -> T): T {
    return keyLocks.withLockByHash(lockHash, task)
  }
}
