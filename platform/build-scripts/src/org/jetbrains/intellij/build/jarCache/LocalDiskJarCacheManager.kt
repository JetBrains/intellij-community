// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.jarCache

import com.intellij.util.io.sha3_224
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.CacheDirCleanup
import java.math.BigInteger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import kotlin.time.Duration.Companion.days

private const val jarSuffix = ".jar"
private const val metaSuffix = ".json"

private const val cacheVersion: Byte = 6

private val json by lazy {
  Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
    isLenient = true
  }
}

internal class LocalDiskJarCacheManager(private val cacheDir: Path, private val classOutDirectory: Path) : JarCacheManager {
  init {
    Files.createDirectories(cacheDir)
  }

  override suspend fun cleanup() {
    withContext(Dispatchers.IO) {
      CacheDirCleanup(cacheDir = cacheDir, maxAccessTimeAge = 2.days).runCleanupIfRequired()
    }
  }

  override suspend fun computeIfAbsent(sources: List<Source>,
                                       targetFile: Path,
                                       nativeFiles: MutableMap<ZipSource, List<String>>?,
                                       span: Span,
                                       producer: SourceBuilder): Path {
    val items = createSourceAndCacheStrategyList(sources = sources, classOutDirectory = classOutDirectory)

    // 224 bit and not 256/512 - use a slightly shorter filename
    // xxh3 is not used as it is not secure and moreover, better to stick to JDK API
    val hash = sha3_224()
    hash.update(cacheVersion)
    hashAsShort(hash, items.size)
    for (source in items) {
      hashAsShort(hash, source.path.length)
      hash.update(source.path.encodeToByteArray())

      source.updateDigest(hash)
    }

    producer.updateDigest(hash)

    val hashString = BigInteger(1, hash.digest()).toString(Character.MAX_RADIX)
    val cacheName = "${targetFile.fileName.toString().removeSuffix(jarSuffix)}-$hashString"
    val cacheFileName = (cacheName + jarSuffix).takeLast(255)
    val cacheFile = cacheDir.resolve(cacheFileName)
    val cacheMetadataFile = cacheDir.resolve((cacheName + metaSuffix).takeLast(255))
    if (checkCache(cacheMetadataFile = cacheMetadataFile,
                   cacheFile = cacheFile,
                   sources = sources,
                   items = items,
                   span = span,
                   nativeFiles = nativeFiles)) {
      if (!producer.useCacheAsTargetFile) {
        Files.createDirectories(targetFile.parent)
        Files.copy(cacheFile, targetFile)
      }
      span.addEvent(
        "use cache",
        Attributes.of(
          AttributeKey.stringKey("file"), targetFile.toString(),
          AttributeKey.stringKey("cacheFile"), cacheFileName,
        ),
      )

      // update file modification time to maintain FIFO caches i.e., in persistent cache folder on TeamCity agent and for CacheDirCleanup
      Files.setLastModifiedTime(cacheFile, FileTime.from(Instant.now()))

      return if (producer.useCacheAsTargetFile) cacheFile else targetFile
    }

    val tempFile = cacheDir.resolve("$cacheName.temp-${java.lang.Long.toUnsignedString(System.currentTimeMillis(), Character.MAX_RADIX)}"
                                  .takeLast(255))
    var fileMoved = false
    try {
      producer.produce(tempFile)
      try {
        Files.move(tempFile, cacheFile)
        fileMoved = true
      }
      catch (e: FileAlreadyExistsException) {
        // concurrent access?
        span.addEvent("cache file $cacheFile already exists")
        check(Files.size(tempFile) == Files.size(cacheFile)) {
          "file=$targetFile, cacheFile=$cacheFile, sources=$sources"
        }
      }
    }
    finally {
      if (!fileMoved) {
        Files.deleteIfExists(tempFile)
      }
    }

    val sourceCacheItems = items.map { source ->
      SourceCacheItem(path = source.path,
                      size = source.getSize().toInt(),
                      hash = source.getHash(),
                      nativeFiles = (source.source as? ZipSource)?.let { nativeFiles?.get(it) } ?: emptyList())
    }

    coroutineScope {
      if (!producer.useCacheAsTargetFile) {
        launch {
          Files.createDirectories(targetFile.parent)
          Files.copy(cacheFile, targetFile)
        }
      }

      launch {
        Files.writeString(cacheMetadataFile, json.encodeToString(JarCacheItem(sources = sourceCacheItems)))
      }

      launch(Dispatchers.Default) {
        notifyAboutMetadata(sources = sourceCacheItems, items = items, nativeFiles = nativeFiles)
      }
    }

    return if (producer.useCacheAsTargetFile) cacheFile else targetFile
  }

  override fun validateHash(source: Source) {
    if (source.hash == 0L && (source !is DirSource || Files.exists(source.dir))) {
      Span.current().addEvent("zero hash for $source")
    }
  }
}

private fun hashAsShort(hash: MessageDigest, value: Int) {
  hash.update(value.toByte())
  hash.update((value shr 8).toByte())
}

private fun checkCache(cacheMetadataFile: Path,
                       cacheFile: Path,
                       sources: List<Source>,
                       items: List<SourceAndCacheStrategy>,
                       nativeFiles: MutableMap<ZipSource, List<String>>?,
                       span: Span): Boolean {
  if (Files.notExists(cacheMetadataFile)) {
    Files.deleteIfExists(cacheFile)
    return false
  }

  if (Files.notExists(cacheFile)) {
    Files.deleteIfExists(cacheMetadataFile)
    return false
  }

  val metadataString = Files.readString(cacheMetadataFile)
  val metadata = try {
    json.decodeFromString<JarCacheItem>(metadataString)
  }
  catch (e: SerializationException) {
    span.addEvent("cannot decode metadata $metadataString", Attributes.of(
      AttributeKey.stringArrayKey("sources"), sources.map { it.toString() },
      AttributeKey.stringKey("error"), e.toString(),
    ))

    Files.deleteIfExists(cacheFile)
    Files.deleteIfExists(cacheMetadataFile)
    return false
  }

  if (!checkSavedAndActualSources(metadata = metadata, sources = sources, items = items)) {
    span.addEvent("metadata $metadataString not equal to $sources")

    Files.deleteIfExists(cacheFile)
    Files.deleteIfExists(cacheMetadataFile)
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
    if (items.get(index).path != metadataItem.path) {
      return false
    }
  }
  return true
}

private fun notifyAboutMetadata(sources: List<SourceCacheItem>,
                                items: List<SourceAndCacheStrategy>,
                                nativeFiles: MutableMap<ZipSource, List<String>>?) {
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
  @JvmField val path: String,
  @JvmField val size: Int,
  @JvmField val hash: Long,
  @JvmField val nativeFiles: List<String> = emptyList(),
)