// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer

// not thread-safe, intended only for single thread for one time use
class ByteBufferAllocator : AutoCloseable {
  private var directByteBuffer: ByteBuffer? = null

  fun allocate(size: Int): ByteBuffer {
    var result = directByteBuffer
    if (result != null && result.capacity() < size) {
      // clear references to object to make sure that it can be collected by GC
      directByteBuffer = null
      unmapBuffer(result)
      result = null
    }

    if (result == null) {
      result = ByteBuffer.allocateDirect(roundUpInt(size, 65_536))!!
      result.order(ByteOrder.LITTLE_ENDIAN)
      directByteBuffer = result
    }
    result.rewind()
    result.limit(size)
    return result
  }

  override fun close() {
    directByteBuffer?.let { unmapBuffer(it) }
  }
}

private val unmap by lazy {
  val unsafeClass = ClassLoader.getPlatformClassLoader().loadClass("sun.misc.Unsafe")
  val lookup = MethodHandles.privateLookupIn(unsafeClass, MethodHandles.lookup())
  val unsafe = lookup.findStaticGetter(unsafeClass, "theUnsafe", unsafeClass).invoke()
  lookup.findVirtual(unsafeClass, "invokeCleaner", MethodType.methodType(Void.TYPE, ByteBuffer::class.java)).bindTo(unsafe)
}

internal fun unmapBuffer(buffer: ByteBuffer) {
  unmap.invokeExact(buffer)
}

private fun roundUpInt(x: Int, @Suppress("SameParameterValue") blockSizePowerOf2: Int): Int {
  return x + blockSizePowerOf2 - 1 and -blockSizePowerOf2
}

internal inline fun useAsLongBuffer(buffer: ByteBuffer, task: (LongBuffer) -> Unit) {
  val longBuffer = buffer.asLongBuffer()
  task(longBuffer)
  buffer.position(buffer.position() + (longBuffer.position() * Long.SIZE_BYTES))
}