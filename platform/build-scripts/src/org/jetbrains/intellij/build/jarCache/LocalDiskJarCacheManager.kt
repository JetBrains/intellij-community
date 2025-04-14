// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.Hashing
import io.netty.buffer.ByteBuf
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.SourceAndCacheStrategy
import org.jetbrains.intellij.build.StripedMutex
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.createSourceAndCacheStrategyList
import org.jetbrains.intellij.build.dependencies.CacheDirCleanup
import org.jetbrains.intellij.build.io.READ_OPEN_OPTION
import org.jetbrains.intellij.build.io.W_OVERWRITE
import org.jetbrains.intellij.build.io.byteBufferAllocator
import org.jetbrains.intellij.build.io.unmapBuffer
import org.jetbrains.intellij.build.io.use
import org.jetbrains.intellij.build.io.writeToFileChannelFully
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

private const val jarSuffix = ".jar"
private const val metaSuffix = ".m"

private val fileLocks = StripedMutex(256)

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
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ): Path {
    val items = createSourceAndCacheStrategyList(sources = sources, productionClassOutDir = productionClassOutDir)
    val hash = Hashing.xxh3_128().hashStream()
    for (source in items) {
      source.updateAssetDigest(hash)
    }
    hash.putInt(items.size)
    val hashValue128 = hash.get()

    fileLocks.getLockByHash(hashValue128.asLong).withLock {
      val targetFileNamePrefix = targetFile.fileName.toString().removeSuffix(jarSuffix)
      val cacheName = "$targetFileNamePrefix-16-${longToString(hashValue128.leastSignificantBits)}-${longToString(hashValue128.mostSignificantBits)}"
      val cacheFileName = (cacheName + jarSuffix).takeLast(255)
      val cacheFile = cacheDir.resolve(cacheFileName)
      val cacheMetadataFile = cacheDir.resolve((cacheName + metaSuffix).takeLast(255))
      if (checkCache(cacheMetadataFile = cacheMetadataFile, cacheFile = cacheFile, sources = sources, items = items, span = span, nativeFiles = nativeFiles, producer = producer)) {
        // update file modification time to maintain FIFO caches, i.e., in a persistent cache folder on TeamCity agent and for CacheDirCleanup
        try {
          Files.setLastModifiedTime(cacheFile, FileTime.from(Instant.now()))
        }
        catch (e: IOException) {
          Span.current().addEvent("update cacheFile modification time failed: $e")
        }

        span.addEvent("use cache", Attributes.of(AttributeKey.stringKey("file"), targetFile.toString(), AttributeKey.stringKey("cacheFile"), cacheFileName))

        if (producer.useCacheAsTargetFile) {
          return cacheFile
        }
        else {
          Files.createDirectories(targetFile.parent)
          Files.createLink(targetFile, cacheFile)
          return targetFile
        }
      }
      else {
        return produceAndCache(
          cacheName = cacheName,
          cacheDir = cacheDir,
          producer = producer,
          cacheFile = cacheFile,
          targetFile = targetFile,
          items = items,
          nativeFiles = nativeFiles,
          cacheMetadataFile = cacheMetadataFile,
        )
      }
    }
  }
}

private val tempFilePrefix = longToString(ProcessHandle.current().pid())

private suspend fun produceAndCache(
  cacheName: String,
  producer: SourceBuilder,
  cacheFile: Path,
  targetFile: Path,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  cacheMetadataFile: Path,
  cacheDir: Path,
): Path {
  val tempFile = cacheDir.resolve("$cacheName.$tempFilePrefix-${longToString(Random.nextLong())}".takeLast(255))
  var fileMoved = false
  try {
    producer.produce(tempFile)
    try {
      Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING)
    }
    fileMoved = true
  }
  finally {
    if (!fileMoved) {
      Files.deleteIfExists(tempFile)
    }
  }

  if (!producer.useCacheAsTargetFile) {
    Files.createDirectories(targetFile.parent)
    try {
      Files.createLink(targetFile, cacheFile)
    }
    catch (e: IOException) {
      throw RuntimeException("Cannot create link from $cacheFile to $targetFile (cacheFile exists: ${Files.exists(cacheFile)})", e)
    }
  }

  val sourceCacheItems = Array(items.size) { index ->
    val source = items.get(index)
    SourceCacheItem(
      size = source.getSize().toInt(),
      hash = source.getHash(),
      nativeFiles = (source.source as? ZipSource)?.let { nativeFiles?.get(it) } ?: emptyList(),
    )
  }

  byteBufferAllocator.ioBuffer(sourceCacheItems.sumOf { it.estimateSerializedSize() }).use { buffer ->
    writeSourcesTo(sourceCacheItems, buffer)
    FileChannel.open(cacheMetadataFile, W_OVERWRITE).use {
      writeToFileChannelFully(channel = it, position = 0, buffer = buffer)
    }
  }

  notifyAboutMetadata(sources = sourceCacheItems, items = items, nativeFiles = nativeFiles, producer = producer)

  return if (producer.useCacheAsTargetFile) cacheFile else targetFile
}

private fun longToString(v: Long): String = java.lang.Long.toUnsignedString(v, Character.MAX_RADIX)

private fun checkCache(
  cacheMetadataFile: Path,
  cacheFile: Path,
  sources: Collection<Source>,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  span: Span,
  producer: SourceBuilder,
): Boolean {
  if (Files.notExists(cacheMetadataFile) || Files.notExists(cacheFile)) {
    return false
  }

  val savedSources = try {
    val buffer = FileChannel.open(cacheMetadataFile, READ_OPEN_OPTION).use {
      it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
    }

    try {
      readSourcesFrom(buffer)
    }
    finally {
      unmapBuffer(buffer)
    }
  }
  catch (_: NoSuchFileException) {
    return false
  }
  catch (e: Exception) {
    span.addEvent("cannot decode metadata", Attributes.of(
      AttributeKey.stringArrayKey("sources"), sources.map { it.toString() },
      AttributeKey.stringKey("error"), e.toString(),
    ))

    Files.deleteIfExists(cacheMetadataFile)
    Files.deleteIfExists(cacheFile)
    return false
  }

  if (!checkSavedAndActualSources(savedSources = savedSources, sources = sources, items = items)) {
    span.addEvent("metadata not equal to $sources")

    Files.deleteIfExists(cacheMetadataFile)
    Files.deleteIfExists(cacheFile)
    return false
  }

  notifyAboutMetadata(sources = savedSources, items = items, nativeFiles = nativeFiles, producer = producer)
  return true
}

private fun checkSavedAndActualSources(savedSources: Array<SourceCacheItem>, sources: Collection<Source>, items: List<SourceAndCacheStrategy>): Boolean {
  if (savedSources.size != sources.size) {
    return false
  }

  for ((index, metadataItem) in savedSources.withIndex()) {
    if (items.get(index).getHash() != metadataItem.hash) {
      return false
    }
  }
  return true
}

private fun notifyAboutMetadata(
  sources: Array<SourceCacheItem>,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  producer: SourceBuilder,
) {
  for ((index, sourceCacheItem) in sources.withIndex()) {
    val source = items.get(index).source
    producer.consumeInfo(source, sourceCacheItem.size, sourceCacheItem.hash)
    if (sourceCacheItem.nativeFiles.isNotEmpty()) {
      nativeFiles?.put(source as ZipSource, sourceCacheItem.nativeFiles)
    }
  }
}

private fun readSourcesFrom(buffer: ByteBuffer): Array<SourceCacheItem> {
  val sourceCount = buffer.getInt()
  val sources = Array(sourceCount) {
    SourceCacheItem.readFrom(buffer)
  }
  return sources
}

private fun writeSourcesTo(sources: Array<SourceCacheItem>, buffer: ByteBuf) {
  buffer.writeInt(sources.size)
  for (source in sources) {
    source.writeTo(buffer)
  }
}

private class SourceCacheItem(
  @JvmField val size: Int,
  @JvmField val hash: Long,
  @JvmField val nativeFiles: List<String> = emptyList(),
) {
  companion object {
    fun readFrom(buffer: ByteBuffer): SourceCacheItem {
      val size = buffer.getInt()
      val hash = buffer.getLong()

      val nativeFileCount = buffer.getInt()
      if (nativeFileCount == 0) {
        return SourceCacheItem(size = size, hash = hash, nativeFiles = emptyList())
      }

      val nativeFiles = Array(nativeFileCount) {
        val bytes = ByteArray(buffer.getInt())
        buffer.get(bytes)
        bytes.decodeToString()
      }
      return SourceCacheItem(size = size, hash = hash, nativeFiles = nativeFiles.asList())
    }
  }

  fun estimateSerializedSize(): Int {
    // size (Int) + hash (Long) + nativeFiles.size (Int)
    return 4 + 8 + 4 +
           nativeFiles.sumOf {
             // length of bytes (Int) + size of ascii string
             4 + it.length
           }
  }

  fun writeTo(buffer: ByteBuf) {
    buffer.writeInt(size)
    buffer.writeLong(hash)

    buffer.writeInt(nativeFiles.size)
    for (file in nativeFiles) {
      val bytes = file.toByteArray()
      buffer.writeInt(bytes.size)
      buffer.writeBytes(bytes)
    }
  }
}