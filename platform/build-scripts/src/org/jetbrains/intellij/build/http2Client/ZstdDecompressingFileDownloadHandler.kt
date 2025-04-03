// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.ZstdDecompressCtx
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import java.nio.file.Path

private const val MAX_BUFFER_SIZE = 8 * 1014 * 1024

// In Netty HTTP2, all streams use the same single-threaded event loop as we only use one channel.
// Therefore, similar to read operations, using the Netty ChannelHandler concept doesn't make sense -
// it doesn't solve the single-threaded execution problem and also complicates the code (coroutine-based code is more concise and logically grouped).
internal class ZstdDecompressingFileDownloadHandler(
  private val urlPath: CharSequence,
  private val result: CompletableDeferred<DownloadResult>,
  private val downloadResult: DownloadResult,
  private val file: Path,
  private val zstdDecompressContextPool: ZstdDecompressContextPool,
  private val unzip: Boolean,
  private val ignoreNotFound: Boolean,
) : ChannelInboundHandlerAdapter() {
  private var dataConsumer: DataConsumer? = null
  private var zstdDecompressContext: ZstdDecompressCtx? = null

  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    result.completeExceptionally(cause)
  }

  override fun handlerAdded(ctx: ChannelHandlerContext?) {
    dataConsumer = if (unzip) ZipDecoder(file) else DataToFileConsumer(file)
    zstdDecompressContext = zstdDecompressContextPool.allocate()
  }

  override fun handlerRemoved(context: ChannelHandlerContext) {
    try {
      dataConsumer?.close()
      dataConsumer = null
    }
    finally {
      zstdDecompressContext?.let {
        zstdDecompressContext = null
        zstdDecompressContextPool.release(it)
      }
    }
  }

  override fun channelRead(context: ChannelHandlerContext, message: Any) {
    var release = true
    try {
      when (message) {
        is Http2DataFrame -> {
          result.ensureActive()
          if (writeChunk(message, context.alloc())) {
            release = false
          }
          if (message.isEndStream) {
            result.complete(downloadResult)
          }
        }
        is Http2HeadersFrame -> {
          val status = HttpResponseStatus.parseLine(message.headers().status())
          if (status != HttpResponseStatus.OK) {
            if (ignoreNotFound && status == HttpResponseStatus.NOT_FOUND) {
              downloadResult.size = -1
              result.complete(downloadResult)
            }
            else {
              result.completeExceptionally(UnexpectedHttpStatus(urlPath, status))
            }
          }
        }
        else -> {
          release = false
          context.fireChannelRead(message)
        }
      }
    }
    finally {
      if (release) {
        ReferenceCountUtil.release(message)
      }
    }
  }

  // When processing small chunks:
  // - Initially, zstd can't restore the original data, so the target remains empty.
  // - This continues with more small chunks...
  // - Eventually, zstd gets enough data and writes to the target.
  // If we allocate the target size based on the current chunk size * 4, it may not be enough for all the accumulated data, causing more native calls and unnecessary work.
  // So, we track the size of chunks that don't update the target and use this to calculate the next allocation size.
  private var zstdBufferedSize = 0

  private fun writeChunk(message: Http2DataFrame, allocator: ByteBufAllocator): Boolean {
    val source = message.content()
    downloadResult.size += source.readableBytes()

    val sourceNio = source.internalNioBuffer(source.readerIndex(), source.readableBytes())
    val zstdDecompressContext = zstdDecompressContext!!
    val dataConsumer = this@ZstdDecompressingFileDownloadHandler.dataConsumer!!
    val digest = downloadResult.digest
    do {
      val sourceRemaining = sourceNio.remaining()
      val chunkSize = ((zstdBufferedSize + sourceRemaining) * 4).coerceAtMost(MAX_BUFFER_SIZE)
      val target = allocator.directBuffer(chunkSize)
      try {
        val targetNio = target.internalNioBuffer(target.writerIndex(), chunkSize)
        targetNio.mark()
        zstdDecompressContext.decompressDirectByteBufferStream(targetNio, sourceNio)
        // `flip` is not suitable because it sets the position to 0, whereas the NIO buffer from the allocator may have a non-zero initial position
        targetNio.limit(targetNio.position())
        targetNio.reset()
        val remaining = targetNio.remaining()
        if (remaining > 0) {
          zstdBufferedSize = 0

          if (digest != null) {
            targetNio.mark()
            digest.update(targetNio)
            targetNio.reset()
          }

          target.writerIndex(remaining)
          val done = !sourceNio.hasRemaining()
          if (done) {
            // release as soon as possible
            message.release()
          }
          dataConsumer.consume(target)
          if (done) {
            return true
          }
        }
        else {
          zstdBufferedSize += sourceRemaining
        }
      }
      finally {
        target.release()
      }
    }
    while (sourceNio.hasRemaining())
    return false
  }
}