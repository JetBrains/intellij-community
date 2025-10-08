// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.fs.openForReading
import com.intellij.platform.eel.getOr
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.math.max

@OptIn(EelDelicateApi::class)
suspend fun EelFileSystemApi.readFileImpl(args: EelFileSystemApi.ReadFileArgs): EelResult<EelFileSystemApi.ReadFileResult, EelFileSystemApi.FileReaderError> =
  readFileImpl(
    path = args.path,
    initialBuffer = args.buffer,
    mayReturnSameBuffer = args.mayReturnSameBuffer,
    totalLimit = args.limit?.toUInt() ?: UInt.MAX_VALUE,
    failFastIfBeyondLimit = args.failFastIfBeyondLimit,
  )

@OptIn(EelDelicateApi::class)
private suspend fun EelFileSystemApi.readFileImpl(
  path: EelPath,
  initialBuffer: ByteBuffer?,
  mayReturnSameBuffer: Boolean,
  totalLimit: UInt,
  failFastIfBeyondLimit: Boolean,
): EelResult<EelFileSystemApi.ReadFileResult, EelFileSystemApi.FileReaderError> {
  val isTotalLimitSet = totalLimit != UInt.MAX_VALUE
  val totalLimit = totalLimit.coerceAtMost(Int.MAX_VALUE.toUInt()).toInt()
  var buffer: ByteBuffer = initialBuffer ?: ByteBuffer.allocate(totalLimit.coerceAtMost(RECOMMENDED_BUFFER_SIZE))
  buffer.position(0).limit(totalLimit.coerceAtMost(buffer.capacity()))

  val reader = openForReading(path)
    .autoCloseAfterLastChunk(true)
    .readFirstChunkInto(buffer)
    .apply {
      if (failFastIfBeyondLimit) {
        closeImmediatelyIfFileBiggerThan(totalLimit.toLong())
      }
    }
    .eelIt()
    .getOr {
      return it
    }

  if (reader.isClosed == true) {
    return prepareResult(initialBuffer = initialBuffer, buffer = buffer, mayReturnSameBuffer = mayReturnSameBuffer, fullyRead = true)
  }

  try {
    if (buffer.limit() >= totalLimit) {
      val fullyRead = reader.isClosed ?: readOneByteToCheckEof(reader, buffer).getOr {
        return it
      }

      return prepareResult(initialBuffer = initialBuffer, buffer = buffer, mayReturnSameBuffer = mayReturnSameBuffer, fullyRead = fullyRead)
    }

    if (reader.isClosed == true) {
      return prepareResult(initialBuffer = initialBuffer, buffer = buffer, mayReturnSameBuffer = mayReturnSameBuffer, fullyRead = true)
    }

    buffer.position(buffer.limit())

    var fullyRead = false

    mainLoop@ while (true) {
      while (buffer.position() < totalLimit) {
        buffer.limit(
          totalLimit
            .coerceAtMost(buffer.limit() + RECOMMENDED_BUFFER_SIZE)
            .coerceAtMost(buffer.capacity())
        )
        if (buffer.remaining() < MINIMAL_EFFECTIVE_BUFFER_SIZE && buffer.capacity() < totalLimit) {
          break
        }

        val readResult = reader.read(buffer).getOr {
          return EelFsResultImpl.Error(it.error.mapError())
        }

        when (readResult) {
          ReadResult.EOF -> {
            fullyRead = true
            buffer.flip()
            break@mainLoop
          }
          ReadResult.NOT_EOF -> Unit
        }
      }

      buffer.flip()
      if (buffer.limit() >= totalLimit) {
        break
      }

      val newBuffer = ByteBuffer.allocate(run {
        val candidate = newLength(buffer.capacity())
        if (totalLimit < candidate + MINIMAL_EFFECTIVE_BUFFER_SIZE) totalLimit
        else candidate
      })
      newBuffer.put(buffer)
      buffer = newBuffer
    }

    return prepareResult(initialBuffer = initialBuffer, buffer = buffer, mayReturnSameBuffer = mayReturnSameBuffer, fullyRead = fullyRead)
  }
  finally {
    serviceAsync<AsyncCloser>().closeSometimeLater(reader)
  }
}

private suspend fun readOneByteToCheckEof(
  reader: EelOpenedFile.Reader,
  existingBuffer: ByteBuffer,
): EelResult<Boolean, EelFileSystemApi.FileReaderError> {
  val (buffer, restoreValues) =
    if (existingBuffer.limit() == existingBuffer.capacity()) {
      ByteBuffer.allocate(1) to false
    }
    else {
      existingBuffer to true
    }

  if (restoreValues) {
    buffer.position(buffer.limit())
    buffer.limit(buffer.limit() + 1)
  }

  val oldPosition = buffer.position()
  reader.read(buffer).getOr {
    return EelFsResultImpl.Error(it.error.mapError())
  }
  val fullyRead = buffer.position() == oldPosition

  if (restoreValues) {
    buffer.position(0)
    buffer.limit(buffer.limit() - 1)
  }

  return EelFsResultImpl.Ok(fullyRead)
}

private fun prepareResult(
  initialBuffer: ByteBuffer?,
  buffer: ByteBuffer,
  mayReturnSameBuffer: Boolean,
  fullyRead: Boolean,
): EelFsResultImpl.Ok<EelFileSystemApi.ReadFileResult> =
  EelFsResultImpl.Ok(object : EelFileSystemApi.ReadFileResult {
    override val bytes: ByteBuffer =
      if (buffer === initialBuffer && !mayReturnSameBuffer)
        ByteBuffer.allocate(buffer.limit()).put(buffer).flip()
      else
        buffer

    override val fullyRead: Boolean = fullyRead
  })

/**
 * The algorithm tries to not allocate buffers smaller than this threshold.
 *
 * It's useful to have such buffers, but the size doesn't matter.
 * Here is a statistic about the sizes of all files in a personal checkout of the IntelliJ monorepo,
 * including various build caches for Java and Rust projects.
 * This constant is used only when a file can't be read with a single read operation.
 * Hence, files smaller than 131,072 bytes are not considered.
 * Hypothetically, fine-tuning of this constant can spare one read operation.
 * Considering remainders of division by 131,072: if the distribution had peaks or falls,
 * a special value of this constant could cover them.
 * But there's only one peak: for value 0, which requires no specific optimization.
 * Without 0, the distribution of this particular data is almost flat.
 *
 * Therefore, since any value passes, 16 * 1024 is chosen just because the author finds it attractive.
 */
private const val MINIMAL_EFFECTIVE_BUFFER_SIZE = 16 * 1024

/**
 * 1. It's equal to `com.intellij.platform.ijent.spi.IjentSpiConstKt.RECOMMENDED_MAX_PACKET_SIZE`.
 *    Better to keep them in sync, at least for not allocating excessive buffers,
 *    but nothing should break down if they diverge.
 * 2. It's big enough.
 *    Here is a statistic about the sizes of all files in a personal checkout of the IntelliJ monorepo,
 *    including various build caches for Java and Rust projects.
 *    * Total size = 182,106,998,688
 *    * File count = 2,922,717
 *    * Median size = 1369
 *    * P90 = 11,579
 *    * P95 = 30,480
 *    * P97 = 80,104
 *    * P98 = 138,401
 *    * P999 = 8,388,608
 *    * Max size = 27,239,038,774 -- if you're curious, it's one of `.git/objects/pack/pack-*.pack`
 *
 *    This buffer is enough to read more than 97% of all files with a single read operation.
 */
private const val RECOMMENDED_BUFFER_SIZE = 131_072

/** A copy-paste of `jdk.internal.util.ArraysSupport.SOFT_MAX_ARRAY_LENGTH` */
private const val SOFT_MAX_ARRAY_LENGTH = Int.MAX_VALUE - 8

/** A stripped copy-paste of `jdk.internal.util.ArraysSupport.newLength` */
private fun newLength(oldLength: Int): Int {
  val prefLength: Long = oldLength.toLong() + max(RECOMMENDED_BUFFER_SIZE, oldLength)
  if (prefLength <= SOFT_MAX_ARRAY_LENGTH) {
    return prefLength.toInt()
  }
  else {
    // put code cold in a separate method
    return hugeLength(oldLength)
  }
}

/** A copy-paste of `jdk.internal.util.ArraysSupport.hugeLength` */
private fun hugeLength(oldLength: Int): Int {
  val minLength: Long = oldLength.toLong() + RECOMMENDED_BUFFER_SIZE
  if (minLength >= Int.MAX_VALUE) { // overflow
    throw OutOfMemoryError(
      "Required array length $oldLength + $RECOMMENDED_BUFFER_SIZE is too large")
  }
  return minLength.toInt().coerceAtLeast(SOFT_MAX_ARRAY_LENGTH)
}

private fun EelOpenedFile.Reader.ReadError.mapError(): EelFileSystemApi.FileReaderError =
  when (this) {
    is EelOpenedFile.Reader.ReadError.UnknownFile, is EelOpenedFile.Reader.ReadError.InvalidValue -> EelFsResultImpl.Other(where, toString())
    is EelOpenedFile.Reader.ReadError.Other -> EelFsResultImpl.Other(where, message)
  }

@Service
private class AsyncCloser(private val coroutineScope: CoroutineScope) {
  fun closeSometimeLater(reader: EelOpenedFile.Reader) {
    coroutineScope.launch {
      try {
        reader.close().getOrThrow()
      }
      catch (err: Exception) {
        logger<AsyncCloser>().info("Failed to close $reader", err)
      }
    }
  }
}