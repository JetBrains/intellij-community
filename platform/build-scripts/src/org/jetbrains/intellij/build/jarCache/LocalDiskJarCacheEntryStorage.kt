// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.Source
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

internal fun tryUseCacheEntry(
  key: String,
  paths: CacheEntryPaths,
  targetFile: Path,
  sources: Collection<Source>,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  span: Span,
  producer: SourceBuilder,
  metadataTouchTracker: MetadataTouchTracker,
  cleanupCandidateIndex: CleanupCandidateIndex,
  deleteInvalidEntry: Boolean,
  failOnCacheIoErrors: Boolean,
): Path? {
  // Lock-free path is only safe for materializing into an external target file.
  // If the caller wants cache file as target, keep lock-protected path to avoid
  // returning a path that can be concurrently cleaned up.
  if (!failOnCacheIoErrors) {
    if (producer.useCacheAsTargetFile) {
      return null
    }

    if (Files.exists(paths.markFile)) {
      return null
    }
  }

  val savedSources = readValidCacheMetadata(
    paths = paths,
    sources = sources,
    items = items,
    decodeNativeFiles = nativeFiles != null,
    span = span,
    onInvalidEntry = if (deleteInvalidEntry) {
      { deleteEntryFiles(paths) }
    }
    else {
      null
    },
  ) ?: return null

  val resolvedTarget = if (producer.useCacheAsTargetFile) {
    if (Files.notExists(paths.payloadFile)) {
      return null
    }
    paths.payloadFile
  }
  else {
    try {
      createLinkOrCopy(targetFile = targetFile, cacheFile = paths.payloadFile)
      targetFile
    }
    catch (e: IOException) {
      if (failOnCacheIoErrors) {
        throw e
      }
      span.addEvent("cache hit materialization failed, will retry under lock: $e")
      return null
    }
  }

  val metadataTouchUpdated = touchMetadataFileIfRequired(paths = paths, span = span, metadataTouchTracker = metadataTouchTracker)
  if (failOnCacheIoErrors && metadataTouchUpdated) {
    clearMarkFileIfPresent(paths = paths, span = span)
  }
  cleanupCandidateIndex.register(paths.entryStem, paths.entryShardDir.fileName.toString())

  notifyAboutMetadata(sources = savedSources, items = items, nativeFiles = nativeFiles, producer = producer)
  span.addEvent(
    "use cache",
    Attributes.of(AttributeKey.stringKey("file"), targetFile.toString(), AttributeKey.stringKey("cacheKey"), key),
  )
  return resolvedTarget
}

internal suspend fun produceAndCache(
  paths: CacheEntryPaths,
  producer: SourceBuilder,
  targetFile: Path,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  tempFilePrefix: String,
  metadataTouchTracker: MetadataTouchTracker,
  cleanupCandidateIndex: CleanupCandidateIndex,
): Path = withContext(Dispatchers.IO) {
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

  writeSourcesToMetadata(paths = paths, sources = sourceCacheItems, tempFilePrefix = tempFilePrefix)
  metadataTouchTracker.recordTouch(paths.entryStem, System.currentTimeMillis())
  cleanupCandidateIndex.register(paths.entryStem, paths.entryShardDir.fileName.toString())
  notifyAboutMetadata(sources = sourceCacheItems, items = items, nativeFiles = nativeFiles, producer = producer)

  if (!producer.useCacheAsTargetFile) {
    createLinkOrCopy(targetFile = targetFile, cacheFile = paths.payloadFile)
  }

  if (producer.useCacheAsTargetFile) paths.payloadFile else targetFile
}

private fun createLinkOrCopy(targetFile: Path, cacheFile: Path) {
  if (targetFile == cacheFile) {
    return
  }

  Files.createDirectories(targetFile.parent)
  try {
    Files.deleteIfExists(targetFile)
    Files.createLink(targetFile, cacheFile)
  }
  catch (_: IOException) {
    Files.copy(cacheFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
  }
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
) {
  private val touchIntervalMs = minTouchIntervalMs.coerceAtLeast(0)
  private val lastTouchByEntryStem = ConcurrentHashMap<String, Long>()

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

    return shouldTouch
  }

  fun recordTouch(entryStem: String, now: Long) {
    lastTouchByEntryStem[entryStem] = now
  }

  fun onTouchFailure(entryStem: String, attemptedTouchTime: Long) {
    lastTouchByEntryStem.remove(entryStem, attemptedTouchTime)
  }
}
