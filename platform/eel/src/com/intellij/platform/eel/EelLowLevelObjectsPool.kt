// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelLowLevelObjectsPool.Companion.directByteBuffers
import com.intellij.platform.eel.channels.EelDelicateApi
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.nio.ByteBuffer

/**
 * A trivial pool, for reusable objects. It is intentionally a stack, so hot objects remain in CPU caches.
 * Also, it's suitable only for objects that are not obligatory to close explicitly, like ByteBuffer.
 * Everything else is as simple as possible.
 */
@ApiStatus.Internal
class EelLowLevelObjectsPool<T>(private val maxSize: Int, private val factory: () -> T, private val returnValidator: (T) -> Boolean) {
  companion object {
    /**
     * A pool with direct byte buffers suitable for Eel. In general, nothing also prohibits using it outside Eel.
     *
     * *Beware:* do not try to use the acquired buffer (returned by [borrow]) after returning it to the pool ([returnBack]).
     * Otherwise, hardly debuggable data races appear. There is no compile-time or runtime check that warns you about this!
     *
     * It's not dangerous to occasionally forget to return some buffer to the pool. It only brings to a slight performance penalty.
     */
    @EelDelicateApi
    val directByteBuffers: EelLowLevelObjectsPool<ByteBuffer> = run {
      val capacity = 128 * 1024
      EelLowLevelObjectsPool(
        50,  // This global number is taken at random.
        factory = { ByteBuffer.allocateDirect(capacity) },
        returnValidator = { buffer ->
          if (buffer.isDirect && buffer.capacity() == capacity) {
            buffer.clear()
            true
          }
          else false
        },
      )
    }

    /**
     * A replacement for [directByteBuffers] for being used in some generalized code that expects `EelLowLevelObjectsPool<ByteBuffer>`.
     */
    val fakeByteBufferPool: EelLowLevelObjectsPool<ByteBuffer> = EelLowLevelObjectsPool(
      0,  // 0 means that there's actually no pool inside; it just shares the same API with the pool.
      factory = { ByteBuffer.allocate(65_536) },  // TODO Find a better constant.
      returnValidator = { false },
    )
  }

  private val stack = ArrayDeque<SoftReference<T>>()
  private val referenceQueue = ReferenceQueue<T>()

  fun returnBack(value: T) {
    if (returnValidator(value)) {
      synchronized(this) {
        if (stack.size < maxSize) {
          stack.addLast(SoftReference(value, referenceQueue))
        }
      }
    }
  }

  fun borrow(): T =
    synchronized(this) {
      borrowImpl()
    }
    ?: factory()

  private tailrec fun borrowImpl(): T? {
    val ref = stack.removeLastOrNull() ?: return null
    return ref.get() ?: borrowImpl()
  }
}