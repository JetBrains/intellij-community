// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdDirectBufferCompressingStreamNoFinalizer
import org.jetbrains.intellij.build.telemetry.use
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.use
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val MEDIA_TYPE_JSON = "application/json".toMediaType()
internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)
internal const val MAX_BUFFER_SIZE = 4_000_000

internal fun uploadArchives(reportStatisticValue: (key: String, value: String) -> Unit,
                            config: CompilationCacheUploadConfiguration,
                            metadataJson: String,
                            httpClient: OkHttpClient,
                            items: List<PackAndUploadItem>,
                            bufferPool: DirectFixedSizeByteBufferPool) {
  val uploadedCount = AtomicInteger()
  val uploadedBytes = AtomicLong()
  val reusedCount = AtomicInteger()
  val reusedBytes = AtomicLong()

  var fallbackToHeads = false
  val alreadyUploaded: Set<String> = try {
    if (config.checkFiles) {
      spanBuilder("fetch info about already uploaded files").use {
        HashSet(getFoundAndMissingFiles(metadataJson, config.serverUrl, httpClient).found)
      }
    }
    else {
      emptySet()
    }
  }
  catch (e: Throwable) {
    Span.current().recordException(e, Attributes.of(
      AttributeKey.stringKey("message"), "failed to fetch info about already uploaded files, will fallback to HEAD requests"
    ))
    fallbackToHeads = true
    emptySet()
  }

  ForkJoinTask.invokeAll(items.mapNotNull { item ->
    if (alreadyUploaded.contains(item.name)) {
      reusedCount.getAndIncrement()
      reusedBytes.getAndAdd(Files.size(item.archive))
      return@mapNotNull null
    }

    forkJoinTask(spanBuilder("upload archive").setAttribute("name", item.name).setAttribute("hash", item.hash!!)) {
      val isUploaded = uploadFile(url = "${config.serverUrl}/${config.uploadPrefix}/${item.name}/${item.hash!!}.jar",
                                  file = item.archive,
                                  useHead = fallbackToHeads,
                                  span = Span.current(),
                                  httpClient = httpClient,
                                  bufferPool = bufferPool)
      val size = Files.size(item.archive)
      if (isUploaded) {
        uploadedCount.getAndIncrement()
        uploadedBytes.getAndAdd(size)
      }
      else {
        reusedCount.getAndIncrement()
        reusedBytes.getAndAdd(size)
      }
    }
  })

  Span.current().addEvent("upload complete", Attributes.of(
    AttributeKey.longKey("reusedParts"), reusedCount.get().toLong(),
    AttributeKey.longKey("uploadedParts"), uploadedCount.get().toLong(),

    AttributeKey.longKey("reusedBytes"), reusedBytes.get(),
    AttributeKey.longKey("uploadedBytes"), uploadedBytes.get(),

    AttributeKey.longKey("totalBytes"), reusedBytes.get() + uploadedBytes.get(),
    AttributeKey.longKey("totalCount"), (reusedCount.get() + uploadedCount.get()).toLong(),
  ))

  reportStatisticValue("compile-parts:reused:bytes", reusedBytes.get().toString())
  reportStatisticValue("compile-parts:reused:count", reusedCount.get().toString())

  reportStatisticValue("compile-parts:uploaded:bytes", uploadedBytes.get().toString())
  reportStatisticValue("compile-parts:uploaded:count", uploadedCount.get().toString())

  reportStatisticValue("compile-parts:total:bytes", (reusedBytes.get() + uploadedBytes.get()).toString())
  reportStatisticValue("compile-parts:total:count", (reusedCount.get() + uploadedCount.get()).toString())
}

private fun getFoundAndMissingFiles(metadataJson: String, serverUrl: String, client: OkHttpClient): CheckFilesResponse {
  client.newCall(Request.Builder()
                   .url("$serverUrl/check-files")
                   .post(metadataJson.toRequestBody(MEDIA_TYPE_JSON))
                   .build()).execute().useSuccessful {
    return Json.decodeFromStream(it.body.byteStream())
  }
}

// Using ZSTD dictionary doesn't make the difference, even slightly worse (default compression level 3).
// That's because in our case we compress a relatively large archive of class files.
private fun uploadFile(url: String,
                       file: Path,
                       useHead: Boolean,
                       span: Span,
                       httpClient: OkHttpClient,
                       bufferPool: DirectFixedSizeByteBufferPool): Boolean {
  if (useHead) {
    val request = Request.Builder().url(url).head().build()
    val code = httpClient.newCall(request).execute().use {
      it.code
    }

    when {
      code == 200 -> {
        span.addEvent("already exist on server, nothing to upload", Attributes.of(
          AttributeKey.stringKey("url"), url,
        ))
        return false
      }
      code != 404 -> {
        span.addEvent("responded with unexpected", Attributes.of(
          AttributeKey.longKey("code"), code.toLong(),
          AttributeKey.stringKey("url"), url,
        ))
      }
    }
  }

  val fileSize = Files.size(file)
  if (Zstd.compressBound(fileSize) <= MAX_BUFFER_SIZE) {
    compressSmallFile(file = file, fileSize = fileSize, bufferPool = bufferPool, url = url)
  }
  else {
    val request = Request.Builder()
      .url(url)
      .put(object : RequestBody() {
        override fun contentType() = MEDIA_TYPE_BINARY

        override fun writeTo(sink: BufferedSink) {
          compressFile(file = file, output = sink, bufferPool = bufferPool)
        }
      })
      .build()

    httpClient.newCall(request).execute().useSuccessful { }
  }

  return true
}

private fun compressSmallFile(file: Path, fileSize: Long, bufferPool: DirectFixedSizeByteBufferPool, url: String) {
  val targetBuffer = bufferPool.allocate()
  try {
    var readOffset = 0L
    val sourceBuffer = bufferPool.allocate()
    var isSourceBufferReleased = false
    try {
      FileChannel.open(file, READ_OPERATION).use { input ->
        do {
          readOffset += input.read(sourceBuffer, readOffset)
        }
        while (readOffset < fileSize)
      }
      sourceBuffer.flip()

      Zstd.compress(targetBuffer, sourceBuffer, 3, false)
      targetBuffer.flip()

      bufferPool.release(sourceBuffer)
      isSourceBufferReleased = true
    }
    finally {
      if (!isSourceBufferReleased) {
        bufferPool.release(sourceBuffer)
      }
    }

    val compressedSize = targetBuffer.remaining()

    val request = Request.Builder()
      .url(url)
      .put(object : RequestBody() {
        override fun contentLength() = compressedSize.toLong()

        override fun contentType() = MEDIA_TYPE_BINARY

        override fun writeTo(sink: BufferedSink) {
          targetBuffer.mark()
          sink.write(targetBuffer)
          targetBuffer.reset()
        }
      })
      .build()

    httpClient.newCall(request).execute().useSuccessful { }
  }
  finally {
    bufferPool.release(targetBuffer)
  }
}

private fun compressFile(file: Path, output: BufferedSink, bufferPool: DirectFixedSizeByteBufferPool) {
  val targetBuffer = bufferPool.allocate()
  object : ZstdDirectBufferCompressingStreamNoFinalizer(targetBuffer, 3) {
    override fun flushBuffer(toFlush: ByteBuffer): ByteBuffer {
      toFlush.flip()
      while (toFlush.hasRemaining()) {
        output.write(toFlush)
      }
      toFlush.clear()
      return toFlush
    }

    override fun close() {
      try {
        super.close()
      }
      finally {
        bufferPool.release(targetBuffer)
      }
    }
  }.use { compressor ->
    val sourceBuffer = bufferPool.allocate()
    try {
      var offset = 0L
      FileChannel.open(file, READ_OPERATION).use { input ->
        val fileSize = input.size()
        while (offset < fileSize) {
          val actualBlockSize = (fileSize - offset).toInt()
          if (sourceBuffer.remaining() > actualBlockSize) {
            sourceBuffer.limit(sourceBuffer.position() + actualBlockSize)
          }

          var readOffset = offset
          do {
            readOffset += input.read(sourceBuffer, readOffset)
          }
          while (sourceBuffer.hasRemaining())

          sourceBuffer.flip()
          compressor.compress(sourceBuffer)

          sourceBuffer.clear()
          offset = readOffset
        }
      }
    }
    finally {
      bufferPool.release(sourceBuffer)
    }
  }
}

@Serializable
private data class CheckFilesResponse(
  val found: List<String> = emptyList(),
  val missing: List<String> = emptyList(),
)