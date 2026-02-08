// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.SourceAndCacheStrategy
import org.jetbrains.intellij.build.ZipSource
import org.jetbrains.intellij.build.io.W_OVERWRITE
import org.jetbrains.intellij.build.io.writeToFileChannelFully
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.random.Random
import kotlin.text.Charsets.UTF_8

private const val metadataHeaderSizeBytes = Int.SIZE_BYTES * 3
private const val sourceRecordFixedSizeBytes = Int.SIZE_BYTES + Long.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
private const val nativeFileSeparator: Byte = 0
// See README.md: "Metadata Safety Limits".
private const val maxNativeFileCount = 65_536
private const val maxNativeFilesBlobSizeBytes = 8 * 1024 * 1024

internal fun readValidCacheMetadata(
  paths: CacheEntryPaths,
  sources: Collection<Source>,
  items: List<SourceAndCacheStrategy>,
  decodeNativeFiles: Boolean,
  span: Span,
  onInvalidEntry: (() -> Unit)?,
): Array<SourceCacheItem>? {
  if (Files.notExists(paths.metadataFile) || Files.notExists(paths.payloadFile)) {
    return null
  }

  val savedSources = try {
    readSourcesFrom(metadataFile = paths.metadataFile, decodeNativeFileNames = decodeNativeFiles)
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: Exception) {
    span.addEvent(
      "cannot decode metadata",
      Attributes.of(
        AttributeKey.stringArrayKey("sources"), sources.map { it.toString() },
        AttributeKey.stringKey("error"), e.toString(),
      ),
    )
    onInvalidEntry?.invoke()
    return null
  }

  if (!checkSavedAndActualSources(savedSources = savedSources, items = items)) {
    span.addEvent("metadata not equal to $sources")
    onInvalidEntry?.invoke()
    return null
  }

  return savedSources
}

internal fun notifyAboutMetadata(
  sources: Array<SourceCacheItem>,
  items: List<SourceAndCacheStrategy>,
  nativeFiles: MutableMap<ZipSource, List<String>>?,
  producer: SourceBuilder,
) {
  for ((index, sourceCacheItem) in sources.withIndex()) {
    val source = items[index].source
    producer.consumeInfo(source, sourceCacheItem.size, sourceCacheItem.hash)
    if (sourceCacheItem.nativeFiles.isNotEmpty()) {
      nativeFiles?.put(source as ZipSource, sourceCacheItem.nativeFiles)
    }
  }
}

internal fun writeSourcesToMetadata(paths: CacheEntryPaths, sources: Array<SourceCacheItem>, tempFilePrefix: String) {
  val serializedSources = Array(sources.size) { index ->
    val source = sources[index]
    check(source.nativeFiles.size <= maxNativeFileCount) {
      "Too many native files: ${source.nativeFiles.size}"
    }
    val nativeFilesBlob = encodeNativeFiles(source.nativeFiles)
    SerializedSourceCacheItem(
      size = source.size,
      hash = source.hash,
      nativeFileCount = source.nativeFiles.size,
      nativeFilesBlob = nativeFilesBlob,
    )
  }

  val metadataSize = metadataHeaderSizeBytes.toLong() +
                     serializedSources.sumOf { sourceRecordFixedSizeBytes.toLong() + it.nativeFilesBlob.size.toLong() }
  check(metadataSize <= Int.MAX_VALUE.toLong()) {
    "Metadata is too large: $metadataSize bytes"
  }

  val tempMetadataFileName = buildTempSiblingFileName(
    baseFileName = paths.metadataFile.fileName.toString(),
    tempFilePrefix = tempFilePrefix,
    randomSuffix = Random.nextLong(),
  )
  val tempMetadataFile = paths.entryShardDir.resolve(tempMetadataFileName)
  var metadataMoved = false
  try {
    val buffer = ByteBuffer.allocate(metadataSize.toInt())
    writeSourcesTo(sources = serializedSources, buffer = buffer)
    buffer.flip()
    FileChannel.open(tempMetadataFile, W_OVERWRITE).use {
      writeToFileChannelFully(channel = it, data = buffer)
    }
    moveReplacing(from = tempMetadataFile, to = paths.metadataFile)
    metadataMoved = true
  }
  finally {
    if (!metadataMoved) {
      Files.deleteIfExists(tempMetadataFile)
    }
  }
}

private fun checkSavedAndActualSources(savedSources: Array<SourceCacheItem>, items: List<SourceAndCacheStrategy>): Boolean {
  if (savedSources.size != items.size) {
    return false
  }

  for ((index, metadataItem) in savedSources.withIndex()) {
    val item = items[index]
    val actualSize = item.getSize()
    if (actualSize !in 0..Int.MAX_VALUE.toLong()) {
      return false
    }

    if (metadataItem.size != actualSize.toInt()) {
      return false
    }

    if (item.getHash() != metadataItem.hash) {
      return false
    }

    if (metadataItem.nativeFiles.isNotEmpty() && item.source !is ZipSource) {
      return false
    }
  }
  return true
}

private fun readSourcesFrom(metadataFile: Path, decodeNativeFileNames: Boolean): Array<SourceCacheItem> {
  FileChannel.open(metadataFile, StandardOpenOption.READ).use { channel ->
    val metadataSize = channel.size()
    check(metadataSize >= metadataHeaderSizeBytes.toLong()) { "Metadata is too short" }

    val headerBuffer = ByteBuffer.allocate(metadataHeaderSizeBytes)
    readBufferFully(channel = channel, buffer = headerBuffer, errorMessage = "Metadata is too short")
    headerBuffer.flip()

    check(headerBuffer.int == metadataMagic) { "Unknown metadata magic" }
    check(headerBuffer.int == metadataSchemaVersion) { "Unsupported metadata schema version" }

    val sourceCount = headerBuffer.int
    check(sourceCount >= 0) { "Negative source count: $sourceCount" }

    var remainingBytes = metadataSize - metadataHeaderSizeBytes
    val maxSourceCountBySize = remainingBytes / sourceRecordFixedSizeBytes
    check(sourceCount.toLong() <= maxSourceCountBySize) {
      "Source count is too large for metadata size: $sourceCount"
    }

    val fixedRecordBuffer = ByteBuffer.allocate(sourceRecordFixedSizeBytes)
    val sources = Array(sourceCount) {
      check(remainingBytes >= sourceRecordFixedSizeBytes.toLong()) { "Not enough bytes for source metadata" }

      fixedRecordBuffer.clear()
      readBufferFully(channel = channel, buffer = fixedRecordBuffer, errorMessage = "Not enough bytes for source metadata")
      fixedRecordBuffer.flip()
      remainingBytes -= sourceRecordFixedSizeBytes.toLong()

      val size = fixedRecordBuffer.int
      check(size >= 0) { "Negative source size: $size" }
      val hash = fixedRecordBuffer.long

      val nativeFileCount = fixedRecordBuffer.int
      check(nativeFileCount >= 0) { "Negative native file count: $nativeFileCount" }
      check(nativeFileCount <= maxNativeFileCount) { "Too many native files: $nativeFileCount" }
      val nativeFilesBlobSize = fixedRecordBuffer.int
      check(nativeFilesBlobSize >= 0) { "Negative native files blob size: $nativeFilesBlobSize" }
      check(nativeFilesBlobSize <= maxNativeFilesBlobSizeBytes) { "Native files blob is too large: $nativeFilesBlobSize" }
      check(remainingBytes >= nativeFilesBlobSize.toLong()) { "Not enough bytes for native files blob" }

      val nativeFiles = if (decodeNativeFileNames) {
        val nativeFilesBlob = ByteArray(nativeFilesBlobSize)
        if (nativeFilesBlob.isNotEmpty()) {
          readBufferFully(channel = channel, buffer = ByteBuffer.wrap(nativeFilesBlob), errorMessage = "Not enough bytes for native files blob")
        }
        remainingBytes -= nativeFilesBlobSize.toLong()
        decodeNativeFiles(nativeFilesBlob = nativeFilesBlob, nativeFileCount = nativeFileCount)
      }
      else {
        if (nativeFilesBlobSize > 0) {
          channel.position(channel.position() + nativeFilesBlobSize.toLong())
        }
        remainingBytes -= nativeFilesBlobSize.toLong()
        emptyList()
      }

      SourceCacheItem(size = size, hash = hash, nativeFiles = nativeFiles)
    }

    check(remainingBytes == 0L) { "Unexpected bytes left in metadata" }
    return sources
  }
}

private fun readBufferFully(channel: FileChannel, buffer: ByteBuffer, errorMessage: String) {
  while (buffer.hasRemaining()) {
    check(channel.read(buffer) > 0) { errorMessage }
  }
}

private class SerializedSourceCacheItem(
  @JvmField val size: Int,
  @JvmField val hash: Long,
  @JvmField val nativeFileCount: Int,
  @JvmField val nativeFilesBlob: ByteArray,
)

private fun writeSourcesTo(sources: Array<SerializedSourceCacheItem>, buffer: ByteBuffer) {
  buffer.putInt(metadataMagic)
  buffer.putInt(metadataSchemaVersion)
  buffer.putInt(sources.size)
  for (source in sources) {
    buffer.putInt(source.size)
    buffer.putLong(source.hash)
    buffer.putInt(source.nativeFileCount)
    buffer.putInt(source.nativeFilesBlob.size)
    buffer.put(source.nativeFilesBlob)
  }
}

private fun encodeNativeFiles(nativeFiles: List<String>): ByteArray {
  if (nativeFiles.isEmpty()) {
    return ByteArray(0)
  }

  val encodedFiles = ArrayList<ByteArray>(nativeFiles.size)
  var totalBlobSize = nativeFiles.size - 1
  for (nativeFile in nativeFiles) {
    check(nativeFile.indexOf('\u0000') < 0) {
      "Native file name contains unsupported NUL character"
    }
    val bytes = nativeFile.toByteArray(UTF_8)
    encodedFiles.add(bytes)
    totalBlobSize += bytes.size
  }

  check(totalBlobSize <= maxNativeFilesBlobSizeBytes) {
    "Native files blob is too large: $totalBlobSize"
  }

  val blob = ByteArray(totalBlobSize)
  var offset = 0
  for ((index, bytes) in encodedFiles.withIndex()) {
    bytes.copyInto(blob, destinationOffset = offset)
    offset += bytes.size
    if (index != encodedFiles.lastIndex) {
      blob[offset] = nativeFileSeparator
      offset++
    }
  }
  return blob
}

private fun decodeNativeFiles(nativeFilesBlob: ByteArray, nativeFileCount: Int): List<String> {
  if (nativeFileCount == 0) {
    check(nativeFilesBlob.isEmpty()) {
      "Unexpected native files blob for empty native file list"
    }
    return emptyList()
  }

  val nativeFiles = ArrayList<String>(nativeFileCount)
  var startOffset = 0
  for (index in 0 until nativeFileCount - 1) {
    val separatorIndex = findSeparatorIndex(nativeFilesBlob = nativeFilesBlob, startOffset = startOffset)
    check(separatorIndex >= 0) {
      "Malformed native file blob: missing separator for index $index"
    }
    nativeFiles.add(nativeFilesBlob.decodeToString(startIndex = startOffset, endIndex = separatorIndex))
    startOffset = separatorIndex + 1
  }

  check(findSeparatorIndex(nativeFilesBlob = nativeFilesBlob, startOffset = startOffset) == -1) {
    "Malformed native file blob: unexpected separator in last entry"
  }
  nativeFiles.add(nativeFilesBlob.decodeToString(startIndex = startOffset, endIndex = nativeFilesBlob.size))
  return nativeFiles
}

private fun findSeparatorIndex(nativeFilesBlob: ByteArray, startOffset: Int): Int {
  for (index in startOffset until nativeFilesBlob.size) {
    if (nativeFilesBlob[index] == nativeFileSeparator) {
      return index
    }
  }
  return -1
}

internal class SourceCacheItem(
  @JvmField val size: Int,
  @JvmField val hash: Long,
  @JvmField val nativeFiles: List<String> = emptyList(),
) 
