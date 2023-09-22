// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.sha3_224
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.dependencies.CacheDirCleanup
import java.math.BigInteger
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*

private const val jarSuffix = ".jar"
private const val metaSuffix = ".json"

private const val cacheVersion: Byte = 4

internal sealed interface JarCacheManager {
  suspend fun computeIfAbsent(item: JarDescriptor,
                              nativeFiles: MutableMap<ZipSource, List<String>>?,
                              span: Span,
                              producer: suspend () -> Unit)
}

internal object NonCachingJarCacheManager : JarCacheManager {
  override suspend fun computeIfAbsent(item: JarDescriptor,
                                       nativeFiles: MutableMap<ZipSource, List<String>>?,
                                       span: Span,
                                       producer: suspend () -> Unit) {
    producer()
  }
}

private val json by lazy {
  Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "  "
    isLenient = true
  }
}

private const val nonMavenLibPathPrefix = "__NOT_MAVEN__/"

internal class LocalDiskJarCacheManager(private val cacheDir: Path) : JarCacheManager {
  init {
    Files.createDirectories(cacheDir)
    CacheDirCleanup(cacheDir).runCleanupIfRequired()
  }

  override suspend fun computeIfAbsent(item: JarDescriptor,
                                       nativeFiles: MutableMap<ZipSource, List<String>>?,
                                       span: Span,
                                       producer: suspend () -> Unit) {
    val sourceToRelativePath = TreeMap<String, ZipSource>()
    for (source in item.sources) {
      if (source !is ZipSource) {
        producer()
        return
      }
      else if (!source.file.startsWith(MAVEN_REPO)) {
        sourceToRelativePath.put(nonMavenLibPathPrefix + source.file.toString(), source)
      }
      else {
        val path = MAVEN_REPO.relativize(source.file).toString()
        sourceToRelativePath.put(path, source)
      }
    }

    // 224 bit and not 256/512 - use a slightly shorter filename
    // xxh3 is not used as it is not secure and moreover, better to stick to JDK API
    val hash = sha3_224()
    hash.update(cacheVersion)
    for ((string, source) in sourceToRelativePath) {
      hash.update(string.encodeToByteArray())
      if (string.startsWith(nonMavenLibPathPrefix)) {
        hash.update(Files.readAllBytes(source.file))
      }
      hash.update('-'.code.toByte())
    }

    val targetFile = item.file
    val cacheName = targetFile.fileName.toString().removeSuffix(jarSuffix) +
                    "-" +
                    BigInteger(1, hash.digest()).toString(Character.MAX_RADIX)
    val cacheFileName = cacheName.take(255 - jarSuffix.length - 1) + jarSuffix
    val cacheFile = cacheDir.resolve(cacheFileName)
    val cacheMetadataFile = cacheDir.resolve(cacheName.take(255 - metaSuffix.length) + metaSuffix)
    if (checkCache(cacheMetadataFile = cacheMetadataFile,
                   cacheFile = cacheFile,
                   item = item,
                   sourceToRelativePath = sourceToRelativePath,
                   span = span,
                   nativeFiles = nativeFiles)) {
      Files.createDirectories(targetFile.parent)
      Files.copy(cacheFile, targetFile)
      span.addEvent(
        "use cache",
        Attributes.of(
          AttributeKey.stringKey("file"), item.file.toString(),
          AttributeKey.stringKey("cacheFile"), cacheFileName,
        ),
      )

      // update file modification time to maintain FIFO caches i.e., in persistent cache folder on TeamCity agent and for CacheDirCleanup
      Files.setLastModifiedTime(cacheFile, FileTime.from(Instant.now()))
      return
    }

    producer()

    val metadata = json.encodeToString(JarCacheItem(sources = sourceToRelativePath.map { (path, source) ->
      SourceCacheItem(path = path,
                      size = Files.size(source.file).toInt(),
                      nativeFiles = nativeFiles?.get(source) ?: emptyList())
    }))

    // first, write metadata
    Files.writeString(cacheMetadataFile, metadata)

    try {
      Files.copy(targetFile, cacheFile)
    }
    catch (e: FileAlreadyExistsException) {
      // concurrent access?
      span.addEvent("Cache file $cacheFile already exists")
      check(Files.size(targetFile) == Files.size(cacheFile))
    }
  }

  private fun checkCache(cacheMetadataFile: Path,
                         cacheFile: Path,
                         item: JarDescriptor,
                         sourceToRelativePath: Map<String, ZipSource>,
                         nativeFiles: MutableMap<ZipSource, List<String>>?,
                         span: Span): Boolean {
    if (Files.notExists(cacheMetadataFile) || Files.notExists(cacheFile)) {
      return false
    }

    val metadataString = Files.readString(cacheMetadataFile)
    val metadata = try {
      json.decodeFromString<JarCacheItem>(metadataString)
    }
    catch (e: SerializationException) {
      span.addEvent("Metadata $metadataString not equal to ${item.sources}",
                    Attributes.of(AttributeKey.stringKey("error"), e.toString()))
      return false
    }

    if (metadata.sources.size != item.sources.size || !metadata.sources.all { sourceToRelativePath.containsKey(it.path) }) {
      span.addEvent("Metadata $metadataString not equal to ${item.sources}")
      return false
    }

    for (sourceCacheItem in metadata.sources) {
      val source = sourceToRelativePath.getValue(sourceCacheItem.path)
      source.sizeConsumer?.accept(sourceCacheItem.size)
      if (sourceCacheItem.nativeFiles.isNotEmpty()) {
        nativeFiles?.put(source, sourceCacheItem.nativeFiles)
      }
    }
    return true
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
  @JvmField val nativeFiles: List<String> = emptyList(),
)