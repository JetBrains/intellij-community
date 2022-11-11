// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.util.lang.ByteBufferCleaner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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