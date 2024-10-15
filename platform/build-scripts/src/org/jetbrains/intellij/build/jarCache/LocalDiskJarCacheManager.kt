// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.Hashing
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.CacheDirCleanup
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val jarSuffix = ".jar"
private const val metaSuffix = ".bin"

private const val cacheVersion: Byte = 11

internal class LocalDiskJarCacheManager(
  private val cacheDir: Path,
  private val productionClassOutDir: Path,
) : JarCacheManager {
  init {
    Files.createDirectories(cacheDir)
  }

  override suspend fun cleanup() {
    val cacheDirCleanup = CacheDirCleanup(cacheDir = cacheDir, maxAccessTimeAge = 7.days)
    withContext(Dispatchers.IO) {
      cacheDirCleanup.runCleanupIfRequired()
    }
  }

  override suspend fun computeIfAbsent(
    sources: List<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ): Path {
    val items = createSourceAndCacheStrategyList(sources = sources, productionClassOutDir = productionClassOutDir)

    val targetFileNamePrefix = targetFile.fileName.toString().removeSuffix(jarSuffix)

    val hash = Hashing.komihash5_0().hashStream()
    hash.putByte(cacheVersion)
    for (source in items) {
      source.updateAssetDigest(hash)
    }
    hash.putInt(items.size)

    val hash1 = java.lang.Long.toUnsignedString(hash.asLong, Character.MAX_RADIX)

    // another 64-bit hash without `source.updateAssetDigest` to reduce the chance of collision
    hash.reset()
    hash.putByte(cacheVersion)
    for (source in items) {
      hash.putLong(source.getHash())
    }
    hash.putInt(items.size)
    producer.updateDigest(hash)

    val hash2 = java.lang.Long.toUnsignedString(hash.asLong, Character.MAX_RADIX)

    val cacheName = "$targetFileNamePrefix-$hash1-$hash2"
    val cacheFileName = (cacheName + jarSuffix).takeLast(255)
    val cacheFile = cacheDir.resolve(cacheFileName)
    val cacheMetadataFile = cacheDir.resolve((cacheName + metaSuffix).takeLast(255))
    if (checkCache(cacheMetadataFile = cacheMetadataFile, cacheFile = cacheFile, sources = sources, items = items, span = span, nativeFiles = nativeFiles)) {
      // update file modification time to maintain FIFO caches i.e., in persistent cache folder on TeamCity agent and for CacheDirCleanup
      Files.setLastModifiedTime(cacheFile, FileTime.from(Instant.now()))

      span.addEvent(
        "use cache",
        Attributes.of(
          AttributeKey.stringKey("file"), targetFile.toString(),
          AttributeKey.stringKey("cacheFile"), cacheFileName,
        ),
      )

      if (producer.useCacheAsTargetFile) {
        return cacheFile
      }
      else {
        Files.createDirectories(targetFile.parent)
        Files.copy(cacheFile, targetFile)
        return targetFile
      }
    }

    val tempFile = cacheDir.resolve("$cacheName.t-${Integer.toUnsignedString(Random.nextInt(), Character.MAX_RADIX)}".takeLast(255))
    var fileMoved = false
    try {
      producer.produce(tempFile)
      try {
        Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      }
      catch (e: AtomicMoveNotSupportedException) {
        Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING)
      }
      fileMoved = true
    }
    finally {
      if (!fileMoved) {
        Files.deleteIfExists(tempFile)
      }
    }

    val sourceCacheItems = Array(items.size) { index ->
      val source = items.get(index)
      SourceCacheItem(
        size = source.getSize().toInt(),
        hash = source.getHash(),
        nativeFiles = (source.source as? ZipSource)?.let { nativeFiles?.get(it) } ?: emptyList(),
      )
    }.asList()

    if (!producer.useCacheAsTargetFile) {
      Files.createDirectories(targetFile.parent)
      Files.copy(cacheFile, targetFile)
    }

    Files.write(cacheMetadataFile, ProtoBuf.encodeToByteArray(JarCacheItem(sources = sourceCacheItems)))

    notifyAboutMetadata(sources = sourceCacheItems, items = items, nativeFiles = nativeFiles)

    return if (producer.useCacheAsTargetFile) cacheFile else targetFile
  }

  override fun validateHash(source: Source) {
    if (source.hash != 0L) {
      return
    }

    if (source is InMemoryContentSource && source.relativePath == "META-INF/plugin.xml") {
      // plugin.xml is not being packed - it is a part of dist meta-descriptor
      return
    }

    if (source is DirSource && Files.notExists(source.dir)) {
      // not existent dir are not packed
      return
    }

    Span.current().addEvent("zero hash for $source")
  }
}

private fun checkCache(cacheMetadataFile: Path,
                       cacheFile: Path,
                       sources: List<Source>,
                       items: List<SourceAndCacheStrategy>,
                       nativeFiles: MutableMap<ZipSource, List<String>>?,
                       span: Span): Boolean {
  if (Files.notExists(cacheMetadataFile) || Files.notExists(cacheFile)) {
    return false
  }

  val metadataBytes = Files.readAllBytes(cacheMetadataFile)
  val metadata = try {
    ProtoBuf.decodeFromByteArray<JarCacheItem>(metadataBytes)
  }
  catch (e: SerializationException) {
    span.addEvent("cannot decode metadata $metadataBytes", Attributes.of(
      AttributeKey.stringArrayKey("sources"), sources.map { it.toString() },
      AttributeKey.stringKey("error"), e.toString(),
    ))

    Files.deleteIfExists(cacheMetadataFile)
    Files.deleteIfExists(cacheFile)
    return false
  }

  if (!checkSavedAndActualSources(metadata = metadata, sources = sources, items = items)) {
    span.addEvent("metadata $metadataBytes not equal to $sources")

    Files.deleteIfExists(cacheMetadataFile)
    Files.deleteIfExists(cacheFile)
    return false
  }

  notifyAboutMetadata(sources = metadata.sources, items = items, nativeFiles = nativeFiles)
  return true
}

private fun checkSavedAndActualSources(metadata: JarCacheItem, sources: List<Source>, items: List<SourceAndCacheStrategy>): Boolean {
  if (metadata.sources.size != sources.size) {
    return false
  }

  for ((index, metadataItem) in metadata.sources.withIndex()) {
    if (items.get(index).getHash() != metadataItem.hash) {
      return false
    }
  }
  return true
}

private fun notifyAboutMetadata(
  sources: List<SourceCacheItem>,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
) {
  for ((index, sourceCacheItem) in sources.withIndex()) {
    val source = items.get(index).source
    source.size = sourceCacheItem.size
    source.hash = sourceCacheItem.hash
    if (sourceCacheItem.nativeFiles.isNotEmpty()) {
      nativeFiles?.put(source as ZipSource, sourceCacheItem.nativeFiles)
    }
  }
}

@Serializable
private class JarCacheItem(
  @JvmField val sources: List<SourceCacheItem> = emptyList(),
)

@Serializable
private class SourceCacheItem(
  @JvmField val size: Int,
  @JvmField val hash: Long,
  @JvmField val nativeFiles: List<String> = emptyList(),
)