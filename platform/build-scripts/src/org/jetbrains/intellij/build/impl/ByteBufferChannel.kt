// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

internal class ByteBufferChannel(private val source: ByteBuffer) : SeekableByteChannel {
  private val size = source.remaining().toLong()

  @Synchronized
  override fun read(destionation: ByteBuffer): Int {
    if (!source.hasRemaining()) {
      return -1
    }

    val count = min(destionation.remaining(), source.remaining())
    if (count > 0) {
      val sourcePosition = source.position()
      destionation.put(destionation.position(), source, sourcePosition, count)
      destionation.position(destionation.position() + count)
      source.position(sourcePosition + count)
    }
    return count
  }

  @Synchronized
  override fun write(src: ByteBuffer): Int {
    throw UnsupportedOperationException()
  }

  @Synchronized
  override fun position(): Long = source.position().toLong()

  @Synchronized
  override fun position(newPosition: Long): ByteBufferChannel {
    require(newPosition or Int.MAX_VALUE - newPosition >= 0)
    source.position(newPosition.toInt())
    return this
  }

  @Synchronized
  override fun size(): Long = size

  @Synchronized
  override fun truncate(size: Long): ByteBufferChannel {
    throw UnsupportedOperationException()
  }

  override fun isOpen(): Boolean = true

  override fun close() {}
}