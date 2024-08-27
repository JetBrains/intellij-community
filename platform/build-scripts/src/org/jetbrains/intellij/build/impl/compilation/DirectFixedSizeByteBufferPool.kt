// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.intellij.build.io.unmapBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

internal class DirectFixedSizeByteBufferPool(private val bufferSize: Int, private val maxPoolSize: Int) : AutoCloseable {
  private val pool = ConcurrentLinkedQueue<ByteBuffer>()
  private val count = AtomicInteger()
  @JvmField
  val semaphore: Semaphore = Semaphore(maxPoolSize)

  private fun allocate(): ByteBuffer {
    val result = pool.poll() ?: return ByteBuffer.allocateDirect(bufferSize)
    count.decrementAndGet()
    return result
  }

  suspend inline fun <T> withBuffer(task: (buffer: ByteBuffer) -> T): T {
    return semaphore.withPermit {
      val buffer = allocate()
      try {
        task(buffer)
      }
      finally {
        release(buffer)
      }
    }
  }

  private fun release(buffer: ByteBuffer) {
    buffer.clear()
    buffer.order(ByteOrder.BIG_ENDIAN)
    if (count.incrementAndGet() < maxPoolSize) {
      pool.offer(buffer)
    }
    else {
      count.decrementAndGet()
      unmapBuffer(buffer)
    }
  }

  override fun close() {
    while (true) {
      unmapBuffer(pool.poll() ?: return)
    }
  }
}