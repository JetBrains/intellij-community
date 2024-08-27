// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.util.AsciiString
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.MAX_BUFFER_SIZE
import org.jetbrains.intellij.build.http2Client.writeData
import org.jetbrains.intellij.build.io.unmapBuffer
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

internal suspend fun uploadArchives(
  reportStatisticValue: (key: String, value: String) -> Unit,
  config: CompilationCacheUploadConfiguration,
  metadataJson: String,
  httpConnection: Http2ClientConnection,
  items: List<PackAndUploadItem>,
) {
  val uploadedCount = AtomicInteger()
  val uploadedBytes = AtomicLong()
  val reusedCount = AtomicInteger()
  val reusedBytes = AtomicLong()
  val start = System.nanoTime()

  var fallbackToHeads = false
  val alreadyUploaded: Set<String> = try {
    if (config.checkFiles) {
      spanBuilder("fetch info about already uploaded files").use {
        getFoundAndMissingFiles(metadataJson = metadataJson, urlPathPrefix = config.serverUrlPathPrefix, connection = httpConnection).found
      }
    }
    else {
      emptySet()
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    Span.current().recordException(e, Attributes.of(AttributeKey.stringKey("message"), "failed to fetch info about already uploaded files, will fallback to HEAD requests"))
    fallbackToHeads = true
    emptySet()
  }

  val sourceBlockSize = MAX_BUFFER_SIZE
  val urlPathPrefix = "${config.serverUrlPathPrefix}/${config.uploadUrlPathPrefix}"
  ZstdCompressContextPool().use { zstdCompressContextPool ->
    items.forEachConcurrent(uploadParallelism) { item ->
      if (alreadyUploaded.contains(item.name)) {
        reusedCount.getAndIncrement()
        reusedBytes.getAndAdd(Files.size(item.archive))
        return@forEachConcurrent
      }

      val urlPath = "$urlPathPrefix/${item.name}/${item.hash!!}.jar"
      spanBuilder("upload archive").setAttribute("name", item.name).setAttribute("hash", item.hash!!).use {
        val size = Files.size(item.archive)
        val isUploaded = uploadFile(
          urlPath = urlPath,
          file = item.archive,
          useHead = fallbackToHeads,
          span = Span.current(),
          httpSession = httpConnection,
          fileSize = size,
          sourceBlockSize = sourceBlockSize,
          zstdCompressContextPool = zstdCompressContextPool,
        )
        if (isUploaded) {
          uploadedCount.getAndIncrement()
          uploadedBytes.getAndAdd(size)
        }
        else {
          reusedCount.getAndIncrement()
          reusedBytes.getAndAdd(size)
        }
      }
    }
  }

  Span.current().addEvent(
    "upload complete",
    Attributes.of(
      AttributeKey.longKey("reusedParts"), reusedCount.get().toLong(),
      AttributeKey.longKey("uploadedParts"), uploadedCount.get().toLong(),

      AttributeKey.longKey("reusedBytes"), reusedBytes.get(),
      AttributeKey.longKey("uploadedBytes"), uploadedBytes.get(),

      AttributeKey.longKey("totalBytes"), reusedBytes.get() + uploadedBytes.get(),
      AttributeKey.longKey("totalCount"), (reusedCount.get() + uploadedCount.get()).toLong(),
    )
  )

  reportStatisticValue("compile-parts:reused:bytes", reusedBytes.get().toString())
  reportStatisticValue("compile-parts:reused:count", reusedCount.get().toString())

  reportStatisticValue("compile-parts:uploaded:bytes", uploadedBytes.get().toString())
  reportStatisticValue("compile-parts:uploaded:count", uploadedCount.get().toString())
  reportStatisticValue("compile-parts:uploaded:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

  reportStatisticValue("compile-parts:total:bytes", (reusedBytes.get() + uploadedBytes.get()).toString())
  reportStatisticValue("compile-parts:total:count", (reusedCount.get() + uploadedCount.get()).toString())
}

private suspend fun getFoundAndMissingFiles(metadataJson: String, urlPathPrefix: String, connection: Http2ClientConnection): CheckFilesResponse {
  return connection.post(path = "$urlPathPrefix/check-files", data = metadataJson, contentType = HttpHeaderValues.APPLICATION_JSON)
}

// Using ZSTD dictionary doesn't make the difference, even slightly worse (default compression level 3).
// That's because in our case, we compress a relatively large archive of class files.
private suspend fun uploadFile(
  urlPath: String,
  file: Path,
  useHead: Boolean,
  span: Span,
  httpSession: Http2ClientConnection,
  fileSize: Long,
  sourceBlockSize: Int,
  zstdCompressContextPool: ZstdCompressContextPool,
): Boolean {
  if (useHead) {
    val status = httpSession.head(urlPath)
    if (status == HttpResponseStatus.OK) {
      span.addEvent("already exist on server, nothing to upload", Attributes.of(AttributeKey.stringKey("urlPath"), urlPath))
      return false
    }
    else if (status != HttpResponseStatus.NOT_FOUND) {
      span.addEvent(
        "responded with unexpected",
        Attributes.of(
          AttributeKey.stringKey("status"), status.toString(),
          AttributeKey.stringKey("urlPath"), urlPath
        ),
      )
    }
  }

  require(fileSize > 0)

  val fileBuffer = FileChannel.open(file, READ_OPERATION).use { channel ->
    channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
  }

  try {
    zstdCompressContextPool.withZstd { zstd ->
      httpSession.put(AsciiString.of(urlPath)) { stream ->
        compressAndUpload(
          fileSize = fileSize,
          fileBuffer = fileBuffer,
          sourceBlockSize = sourceBlockSize,
          zstd = zstd,
          stream = stream,
        )
      }
    }
  }
  finally {
    unmapBuffer(fileBuffer)
  }
  return true
}

private suspend fun compressAndUpload(
  fileSize: Long,
  fileBuffer: MappedByteBuffer,
  sourceBlockSize: Int,
  zstd: ZstdCompressCtx,
  stream: Http2StreamChannel,
) {
  var position = 0
  while (true) {
    val chunkSize = min(fileSize - position, sourceBlockSize.toLong()).toInt()
    val targetSize = Zstd.compressBound(chunkSize.toLong()).toInt()
    val targetNettyBuffer = stream.alloc().directBuffer(targetSize)
    val targetBuffer = targetNettyBuffer.nioBuffer(0, targetSize)
    val compressedSize = zstd.compressDirectByteBuffer(
      targetBuffer, // compress into targetBuffer
      targetBuffer.position(), // write compressed data starting at offset position()
      targetSize, // write no more than target block size bytes
      fileBuffer, // read data to compress from fileBuffer
      position, // start reading at position()
      chunkSize, // read chunk size bytes
    )
    assert(compressedSize > 0)
    targetNettyBuffer.writerIndex(targetNettyBuffer.writerIndex() + compressedSize)
    assert(targetNettyBuffer.readableBytes() == compressedSize)

    position += chunkSize

    val endStream = position >= fileSize
    stream.writeData(targetNettyBuffer, endStream)
    if (endStream) {
      break
    }
  }
}

@Serializable
private data class CheckFilesResponse(
  @JvmField val found: HashSet<String> = HashSet(),
)
