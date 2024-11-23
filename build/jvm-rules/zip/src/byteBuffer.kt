// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer
import java.nio.ByteOrder

// not thread-safe, intended only for single thread for one time use
internal class ByteBufferAllocator() : AutoCloseable {
  private var byteBuffer: ByteBuffer? = null

  fun allocate(size: Int): ByteBuffer {
    var result = byteBuffer
    if (result != null && result.capacity() < size) {
      // clear references to object to make sure that it can be collected by GC
      byteBuffer = null
      unmapBuffer(result)
      result = null
    }

    if (result == null) {
      result = ByteBuffer.allocateDirect(roundUpInt(size, 65_536))!!
      result.order(ByteOrder.LITTLE_ENDIAN)
      byteBuffer = result
    }
    result.rewind()
    result.limit(size)
    return result
  }

  override fun close() {
    byteBuffer?.let { unmapBuffer(it) }
  }
}

private val unmap by lazy {
  val unsafeClass = ClassLoader.getPlatformClassLoader().loadClass("sun.misc.Unsafe")
  val lookup = MethodHandles.privateLookupIn(unsafeClass, MethodHandles.lookup())
  val unsafe = lookup.findStaticGetter(unsafeClass, "theUnsafe", unsafeClass).invoke()
  lookup.findVirtual(unsafeClass, "invokeCleaner", MethodType.methodType(Void.TYPE, ByteBuffer::class.java)).bindTo(unsafe)
}

fun unmapBuffer(buffer: ByteBuffer) {
  if (buffer.isDirect) {
    unmap.invokeExact(buffer)
  }
}

private fun roundUpInt(x: Int, @Suppress("SameParameterValue") blockSizePowerOf2: Int): Int {
  return x + blockSizePowerOf2 - 1 and -blockSizePowerOf2
}