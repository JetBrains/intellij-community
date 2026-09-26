// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.SourceAndCacheStrategy
import org.jetbrains.intellij.build.ZipSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val metadataTouchThrottleMaxEntries = 200_000
private const val metadataPublishReconciliationAttempts = 3
private const val metadataPublishReconciliationDelayMs = 20L
private const val metadataTouchFailureGraceMinMs = 60 * 60 * 1000L

/**
 * Copies the cache payload into [targetFile] when the entry is valid, and answers whether it did.
 *
 * [underLock] says whether the caller holds the per-key lock. Without it the call is a fast optimistic probe: it skips
 * a marked entry, leaves an invalid entry in place, and reports an I/O error as a miss so that the caller retries under
 * the lock. Under the lock it deletes an invalid entry, throws on an I/O error, and clears the mark of a reaccessed entry.
 */
internal fun tryUseCacheEntry(
  key: String,
  paths: CacheEntryPaths,
  targetFile: Path,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  span: Span,
  producer: SourceBuilder,
  metadataTouchTracker: MetadataTouchTracker,
  cleanupCandidateIndex: CleanupCandidateIndex,
  underLock: Boolean,
): Boolean {
  // The lock-free path is safe because the payload is copied into the target file, which the cleanup never touches.
  // A marked entry is a cleanup candidate, so it goes through the lock-protected path.
  if (!underLock && Files.exists(paths.markFile)) {
    return false
  }

  val savedSources = readValidCacheMetadata(
    paths = paths,
    items = items,
    decodeNativeFiles = nativeFiles != null,
    span = span,
    onInvalidEntry = if (underLock) {
      { deleteEntryFiles(paths) }
    }
    else {
      null
    },
  ) ?: return false

  try {
    copyCacheEntryPayload(targetFile = targetFile, cacheFile = paths.payloadFile)
  }
  catch (e: IOException) {
    if (underLock) {
      throw e
    }
    span.addEvent("cache hit materialization failed, will retry under lock: $e")
    return false
  }

  val metadataTouchUpdated = touchMetadataFileIfRequired(paths = paths, span = span, metadataTouchTracker = metadataTouchTracker)
  if (underLock && metadataTouchUpdated) {
    clearMarkFileIfPresent(paths = paths, span = span)
  }
  cleanupCandidateIndex.register(paths.entryStem, paths.entryShardDir.fileName.toString())

  notifyAboutMetadata(sources = savedSources, items = items, nativeFiles = nativeFiles, producer = producer)
  span.addEvent(
    "use cache",
    Attributes.of(AttributeKey.stringKey("file"), targetFile.toString(), AttributeKey.stringKey("cacheKey"), key),
  )
  return true
}

internal fun produceAndCache(
  paths: CacheEntryPaths,
  producer: SourceBuilder,
  targetFile: Path,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  tempFilePrefix: String,
  metadataTouchTracker: MetadataTouchTracker,
  cleanupCandidateIndex: CleanupCandidateIndex,
) {
  Files.createDirectories(paths.entryShardDir)
  val tempPayloadFileName = buildTempSiblingFileName(
    baseFileName = paths.payloadFile.fileName.toString(),
    tempFilePrefix = tempFilePrefix,
    randomSuffix = Random.nextLong(),
  )
  val tempPayload = paths.entryShardDir.resolve(tempPayloadFileName)
  var payloadMoved = false
  try {
    producer.produce(tempPayload)
    moveReplacing(from = tempPayload, to = paths.payloadFile)
    payloadMoved = true
  }
  finally {
    if (!payloadMoved) {
      Files.deleteIfExists(tempPayload)
    }
  }

  val sourceCacheItems = Array(items.size) { index ->
    val source = items[index]
    val sourceSize = source.getSize()
    check(sourceSize in 0..Int.MAX_VALUE.toLong()) {
      "Source size is out of supported range: $sourceSize"
    }
    SourceCacheItem(
      size = sourceSize.toInt(),
      hash = source.getHash(),
      nativeFiles = (source.source as? ZipSource)?.let { nativeFiles?.get(it) } ?: emptyList(),
    )
  }

  try {
    writeSourcesToMetadata(paths = paths, sources = sourceCacheItems, tempFilePrefix = tempFilePrefix)
  }
  catch (e: IOException) {
    if (!reconcileMetadataPublishFailure(paths = paths, items = items, decodeNativeFiles = nativeFiles != null)) {
      throw e
    }
  }
  metadataTouchTracker.recordTouch(paths.entryStem, System.currentTimeMillis())
  cleanupCandidateIndex.register(paths.entryStem, paths.entryShardDir.fileName.toString())
  notifyAboutMetadata(sources = sourceCacheItems, items = items, nativeFiles = nativeFiles, producer = producer)
  copyCacheEntryPayload(targetFile = targetFile, cacheFile = paths.payloadFile)
}

private fun reconcileMetadataPublishFailure(
  paths: CacheEntryPaths,
  items: List<SourceAndCacheStrategy>,
  decodeNativeFiles: Boolean,
): Boolean {
  repeat(metadataPublishReconciliationAttempts) { attempt ->
    if (readValidCacheMetadata(
        paths = paths,
        items = items,
        decodeNativeFiles = decodeNativeFiles,
        span = Span.getInvalid(),
        onInvalidEntry = null,
      ) != null
    ) {
      return true
    }

    if (attempt != metadataPublishReconciliationAttempts - 1) {
      Thread.sleep(metadataPublishReconciliationDelayMs)
    }
  }

  return false
}

/**
 * Copies a cache payload to [targetFile], so that the layout owns bytes the cache does not share.
 *
 * This is the only path by which a module or library jar reaches a distribution, so it is also the one place where
 * [StandardCopyOption.COPY_ATTRIBUTES] earns the whole jar set: the option is what makes the JDK attempt the host's
 * copy-on-write path (see `org.jetbrains.intellij.build.io.copyFile`), turning the copy into a metadata-only clone on
 * APFS or a reflinking Linux filesystem. Dropping it would make every dev build write the whole jar set again.
 */
private fun copyCacheEntryPayload(targetFile: Path, cacheFile: Path) {
  Files.createDirectories(targetFile.parent)
  Files.copy(cacheFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
}

private fun touchMetadataFileIfRequired(paths: CacheEntryPaths, span: Span, metadataTouchTracker: MetadataTouchTracker): Boolean {
  // See README.md: "Mtime Contract And Touch Throttle".
  // metadata mtime is treated as last-access timestamp for cleanup.
  val now = System.currentTimeMillis()
  // Marked entries must refresh access signal immediately to avoid stale second-pass deletion.
  val forceTouch = Files.exists(paths.markFile)
  if (!forceTouch && !metadataTouchTracker.shouldTouch(paths.entryStem, now)) {
    return true
  }

  try {
    Files.setLastModifiedTime(paths.metadataFile, FileTime.fromMillis(now))
    metadataTouchTracker.recordTouch(paths.entryStem, now)
    return true
  }
  catch (e: IOException) {
    metadataTouchTracker.onTouchFailure(paths.entryStem, now)
    span.addEvent("update cache metadata modification time failed: $e")
    return false
  }
}

private fun clearMarkFileIfPresent(paths: CacheEntryPaths, span: Span) {
  try {
    Files.deleteIfExists(paths.markFile)
  }
  catch (e: IOException) {
    span.addEvent("clear cache mark file failed: $e")
  }
}

internal class MetadataTouchTracker(
  minTouchIntervalMs: Long = metadataTouchMinInterval.inWholeMilliseconds,
  private val maxEntries: Int = metadataTouchThrottleMaxEntries,
  touchFailureGracePeriodMs: Long = maxOf(minTouchIntervalMs.coerceAtLeast(0) * 2, metadataTouchFailureGraceMinMs),
) {
  private val touchIntervalMs = minTouchIntervalMs.coerceAtLeast(0)
  private val touchFailureGracePeriod = touchFailureGracePeriodMs.coerceAtLeast(0)
  private val lastTouchByEntryStem = ConcurrentHashMap<String, Long>()
  private val touchFailureByEntryStem = ConcurrentHashMap<String, Long>()

  fun shouldTouch(entryStem: String, now: Long): Boolean {
    var shouldTouch = false
    lastTouchByEntryStem.compute(entryStem) { _, lastTouch ->
      if (lastTouch == null || now - lastTouch >= touchIntervalMs) {
        shouldTouch = true
        now
      }
      else {
        lastTouch
      }
    }

    if (lastTouchByEntryStem.size > maxEntries * 2) {
      // Bound memory in pathological workloads; state is advisory and can be rebuilt from subsequent hits.
      lastTouchByEntryStem.clear()
    }

    trimTouchFailureStateIfNeeded()
    return shouldTouch
  }

  fun recordTouch(entryStem: String, now: Long) {
    lastTouchByEntryStem[entryStem] = now
    touchFailureByEntryStem.remove(entryStem)
    trimTouchFailureStateIfNeeded()
  }

  fun onTouchFailure(entryStem: String, attemptedTouchTime: Long) {
    lastTouchByEntryStem.remove(entryStem, attemptedTouchTime)
    touchFailureByEntryStem[entryStem] = attemptedTouchTime
    trimTouchFailureStateIfNeeded()
  }

  fun hasRecentTouchFailure(entryStem: String, now: Long): Boolean {
    val failedTouchTime = touchFailureByEntryStem[entryStem] ?: return false
    if (now - failedTouchTime <= touchFailureGracePeriod) {
      return true
    }

    touchFailureByEntryStem.remove(entryStem, failedTouchTime)
    return false
  }

  private fun trimTouchFailureStateIfNeeded() {
    if (touchFailureByEntryStem.size > maxEntries * 2) {
      touchFailureByEntryStem.clear()
    }
  }
}
