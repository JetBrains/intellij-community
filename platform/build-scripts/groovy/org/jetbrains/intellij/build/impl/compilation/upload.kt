// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import net.jpountz.lz4.LZ4Factory
import net.jpountz.xxhash.XXHashFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

private val MEDIA_TYPE_BINARY = "application/octet-stream".toMediaType()
private val OPEN_OPTIONS = EnumSet.of(StandardOpenOption.READ)

private val compressor = LZ4Factory.fastestJavaInstance().fastCompressor()
private val checksum = XXHashFactory.fastestJavaInstance().hash32()

internal fun uploadFile(url: String, file: Path, useHead: Boolean, span: Span, httpClient: OkHttpClient): Boolean {
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
    .put(object: RequestBody() {
      override fun contentType() = MEDIA_TYPE_BINARY

      override fun writeTo(sink: BufferedSink) {
        FileChannel.open(file, OPEN_OPTIONS).use { channel ->
          val fileSize = channel.size()
          val lz4Out = LZ4FrameWriter(sink, LZ4FrameWriter.BlockSize.SIZE_4MB, fileSize, compressor, checksum)
          var offset = 0L
          val buffer = lz4Out.buffer
          while (offset < fileSize) {
            val oldLimit = buffer.limit()
            val actualBlockSize = (fileSize - offset).toInt()
            if (buffer.remaining() > actualBlockSize) {
              buffer.limit(buffer.position() + actualBlockSize)
            }

            var readOffset = offset
            while (buffer.hasRemaining()) {
              readOffset += channel.read(buffer, readOffset)
            }

            buffer.limit(oldLimit)
            lz4Out.writeBlock()

            offset = readOffset
            lz4Out.writeBlock()
          }

          lz4Out.finish()
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
