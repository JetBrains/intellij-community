// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.util.lang.ByteBufferCleaner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class DirectFixedSizeByteBufferPool(private val bufferSize: Int, maxPoolSize: Int) : AutoCloseable {
  private val pool = Channel<ByteBuffer>(capacity = maxPoolSize)

  fun allocate(): ByteBuffer {
    val result = pool.tryReceive()
    return when {
      result.isSuccess -> result.getOrThrow()
      result.isClosed -> throw IllegalStateException("Pool is closed")
      else -> ByteBuffer.allocateDirect(bufferSize)
    }
  }

  fun release(buffer: ByteBuffer) {
    buffer.clear()
    buffer.order(ByteOrder.BIG_ENDIAN)
    pool.trySend(buffer).getOrElse {
      // if the pool is full, we simply discard the buffer
      ByteBufferCleaner.unmapBuffer(buffer)
    }
  }

  // pool is not expected to be used during releaseAll call
  override fun close() {
    while (true) {
      ByteBufferCleaner.unmapBuffer(pool.tryReceive().getOrNull() ?: break)
    }
    pool.close()
  }
}