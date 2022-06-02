// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdDirectBufferCompressingStreamNoFinalizer
import com.intellij.diagnostic.telemetry.forkJoinTask
import com.intellij.diagnostic.telemetry.use
import com.intellij.util.lang.ByteBufferCleaner
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
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal val MEDIA_TYPE_BINARY = "application/octet-stream".toMediaType()
private val MEDIA_TYPE_JSON = "application/json".toMediaType()

internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

internal const val MAX_BUFFER_SIZE = 4_000_000

internal fun uploadArchives(reportStatisticValue: (key: String, value: String) -> Unit,
                            serverUrl: String,
                            metadataJson: String,
                            httpClient: OkHttpClient,
                            items: List<PackAndUploadItem>,
                            bufferPool: DirectFixedSizeByteBufferPool) {
  val uploadedCount = AtomicInteger()
  val uploadedBytes = AtomicLong()
  val reusedCount = AtomicInteger()
  val reusedBytes = AtomicLong()

  val alreadyUploaded = HashSet<String>()
  val fallbackToHeads = try {
    val files = spanBuilder("fetch info about already uploaded files").use {
      getFoundAndMissingFiles(metadataJson, serverUrl, httpClient)
    }
    alreadyUploaded.addAll(files.found)
    false
  }
  catch (e: Throwable) {
    Span.current().recordException(e, Attributes.of(
      AttributeKey.stringKey("message"), "failed to fetch info about already uploaded files, will fallback to HEAD requests"
    ))
    true
  }

  ForkJoinTask.invokeAll(items.mapNotNull { item ->
    if (alreadyUploaded.contains(item.name)) {
      reusedCount.getAndIncrement()
      reusedBytes.getAndAdd(Files.size(item.archive))
      return@mapNotNull null
    }

    forkJoinTask(spanBuilder("upload archive").setAttribute("name", item.name).setAttribute("hash", item.hash!!)) {
      // do not use `.zstd` extension - server uses hard-coded `.jar` extension
      // see https://jetbrains.team/p/iji/repositories/intellij-compile-artifacts/files/d91706d68b22502de56c78cdd6218eab3b395b3f/main-server/batch-files-checker/main.go?tab=source&line=62
      if (uploadFile(url = "$serverUrl/$uploadPrefix/${item.name}/${item.hash!!}.jar",
                     file = item.archive,
                     useHead = fallbackToHeads,
                     span = Span.current(),
                     httpClient = httpClient,
                     bufferPool = bufferPool)) {
        uploadedCount.getAndIncrement()
        uploadedBytes.getAndAdd(Files.size(item.archive))
      }
      else {
        reusedCount.getAndIncrement()
        reusedBytes.getAndAdd(Files.size(item.archive))
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
  val request = Request.Builder()
    .url("$serverUrl/check-files")
    .post(metadataJson.toRequestBody(MEDIA_TYPE_JSON))
    .build()

  return client.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
      throw IOException("Failed to check for found and missing files: $response")
    }

    Json.decodeFromStream(response.body.byteStream())
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

  val request = Request.Builder()
    .url(url)
    .put(object : RequestBody() {
      override fun contentType() = MEDIA_TYPE_BINARY

      override fun writeTo(sink: BufferedSink) {
        FileChannel.open(file, READ_OPERATION).use { channel ->
          val fileSize = channel.size()
          if (Zstd.compressBound(fileSize) <= MAX_BUFFER_SIZE) {
            compressSmallFile(channel, fileSize, sink, bufferPool)
            return
          }

          val targetBuffer = bufferPool.allocate()
          object : ZstdDirectBufferCompressingStreamNoFinalizer(targetBuffer, 3) {
            override fun flushBuffer(toFlush: ByteBuffer): ByteBuffer {
              toFlush.flip()
              while (toFlush.hasRemaining()) {
                sink.write(toFlush)
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
              while (offset < fileSize) {
                val actualBlockSize = (fileSize - offset).toInt()
                if (sourceBuffer.remaining() > actualBlockSize) {
                  sourceBuffer.limit(sourceBuffer.position() + actualBlockSize)
                }

                var readOffset = offset
                do {
                  readOffset += channel.read(sourceBuffer, readOffset)
                }
                while (sourceBuffer.hasRemaining())

                sourceBuffer.flip()
                compressor.compress(sourceBuffer)

                sourceBuffer.clear()
                offset = readOffset
              }
            }
            finally {
              bufferPool.release(sourceBuffer)
            }
          }
        }
      }
    })
    .build()
  httpClient.newCall(request).execute().use { response ->
    if (!response.isSuccessful) {
      throw RuntimeException("PUT $url failed with ${response.code}: ${response.body.string()}")
    }
  }
  return true
}

private fun compressSmallFile(channel: FileChannel, fileSize: Long, sink: BufferedSink, bufferPool: DirectFixedSizeByteBufferPool) {
  var readOffset = 0L
  val sourceBuffer = bufferPool.allocate()
  var isSourceBufferReleased = false
  try {
    do {
      readOffset += channel.read(sourceBuffer, readOffset)
    }
    while (readOffset < fileSize)
    sourceBuffer.flip()

    val targetBuffer = bufferPool.allocate()
    try {
      Zstd.compress(targetBuffer, sourceBuffer, 3, false)
      targetBuffer.flip()

      bufferPool.release(sourceBuffer)
      isSourceBufferReleased = true

      sink.write(targetBuffer)
    }
    finally {
      bufferPool.release(targetBuffer)
    }
  }
  finally {
    if (!isSourceBufferReleased) {
      bufferPool.release(sourceBuffer)
    }
  }
}


internal class DirectFixedSizeByteBufferPool(private val size: Int, private val maxPoolSize: Int) : AutoCloseable {
  private val pool = ConcurrentLinkedQueue<ByteBuffer>()

  private val count = AtomicInteger()

  fun allocate(): ByteBuffer {
    val result = pool.poll() ?: return ByteBuffer.allocateDirect(size)
    count.decrementAndGet()
    return result
  }

  fun release(buffer: ByteBuffer) {
    buffer.clear()
    buffer.order(ByteOrder.BIG_ENDIAN)
    if (count.incrementAndGet() < maxPoolSize) {
      pool.add(buffer)
    }
    else {
      count.decrementAndGet()
      ByteBufferCleaner.unmapBuffer(buffer)
    }
  }

  // pool is not expected to be used during releaseAll call
  override fun close() {
    while (true) {
      ByteBufferCleaner.unmapBuffer(pool.poll() ?: return)
    }
  }
}

@Serializable
private data class CheckFilesResponse(
  val found: List<String> = emptyList(),
  val missing: List<String> = emptyList()
)