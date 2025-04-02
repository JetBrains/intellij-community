// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.AdaptiveByteBufAllocator
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer
import java.nio.ByteOrder

val byteBufferAllocator: ByteBufAllocator = run {
  System.setProperty("io.netty.tryReflectionSetAccessible", "true")

  if (System.getProperty("io.netty.allocator.useCachedMagazinesForNonEventLoopThreads") == "true" &&
      System.getProperty("io.netty.allocator.type", "adaptive") == "adaptive") {
    val allocator = ByteBufAllocator.DEFAULT
    if (allocator is AdaptiveByteBufAllocator) {
      return@run allocator
    }
  }
  AdaptiveByteBufAllocator(true, true)
}

// not thread-safe, intended only for single thread for one time use
internal class ByteBufferAllocator() : AutoCloseable {
  private var byteBuf: ByteBuf? = null

  @Synchronized
  fun allocate(size: Int): ByteBuffer {
    var result = byteBuf
    if (result != null && result.capacity() < size) {
      result.release()
      result = null
    }

    if (result == null) {
      result = byteBufferAllocator.directBuffer(roundUpInt(size, 65_536))
      byteBuf = result
    }
    return result.nioBuffer(0, size).order(ByteOrder.LITTLE_ENDIAN)
  }

  @Synchronized
  override fun close() {
    byteBuf?.let {
      byteBuf = null
      it.release()
    }
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

internal fun roundUpInt(x: Int, blockSizePowerOf2: Int): Int {
  return x + blockSizePowerOf2 - 1 and -blockSizePowerOf2
}