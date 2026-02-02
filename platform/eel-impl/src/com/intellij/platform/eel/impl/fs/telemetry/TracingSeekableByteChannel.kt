// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.*
import java.util.concurrent.ConcurrentHashMap

internal fun SeekableByteChannel.traced(spanNamePrefix: String): SeekableByteChannel =
  if (this is FileChannel) {
    TracingFileChannel(delegate = this, spanNamePrefix)
  }
  else {
    TracingSeekableByteChannel(delegate = this, spanNamePrefix)
  }

internal fun FileChannel.traced(spanNamePrefix: String): FileChannel = TracingFileChannel(delegate = this, spanNamePrefix)

internal class TracingSeekableByteChannel(
  private val delegate: SeekableByteChannel,
  private val spanNamePrefix: String,
) : SeekableByteChannel {
  override fun close() {
    Measurer.measure(Measurer.Operation.seekableByteChannelClose, spanNamePrefix) {
      delegate.close()
    }
  }

  override fun isOpen(): Boolean =
    delegate.isOpen

  override fun read(dst: ByteBuffer?): Int =
    Measurer.measure(Measurer.Operation.seekableByteChannelRead, spanNamePrefix) {
      delegate.read(dst)
    }

  override fun write(src: ByteBuffer?): Int =
    Measurer.measure(Measurer.Operation.seekableByteChannelWrite, spanNamePrefix) {
      delegate.write(src)
    }

  override fun position(): Long =
    Measurer.measure(Measurer.Operation.seekableByteChannelPosition, spanNamePrefix) {
      delegate.position()
    }

  override fun position(newPosition: Long): SeekableByteChannel =
    TracingSeekableByteChannel(
      Measurer.measure(Measurer.Operation.seekableByteChannelNewPosition, spanNamePrefix) {
        delegate.position(newPosition)
      },
      spanNamePrefix
    )

  override fun size(): Long =
    Measurer.measure(Measurer.Operation.seekableByteChannelSize, spanNamePrefix) {
      delegate.size()
    }

  override fun truncate(size: Long): SeekableByteChannel =
    Measurer.measure(Measurer.Operation.seekableByteChannelTruncate, spanNamePrefix) {
      delegate.truncate(size)
    }
}

internal class TracingFileChannel(
  private val delegate: FileChannel,
  private val spanNamePrefix: String,
) : FileChannel() {
  private val implCloseChannelMethod: Method = getImplCloseChannelMethod(delegate.javaClass)

  override fun read(dsts: Array<out ByteBuffer?>?, offset: Int, length: Int): Long =
    Measurer.measure(Measurer.Operation.fileChannelRead, spanNamePrefix) {
      delegate.read(dsts, offset, length)
    }

  override fun read(dst: ByteBuffer?, position: Long): Int =
    Measurer.measure(Measurer.Operation.fileChannelRead, spanNamePrefix) {
      delegate.read(dst, position)
    }

  override fun write(srcs: Array<out ByteBuffer?>?, offset: Int, length: Int): Long =
    Measurer.measure(Measurer.Operation.fileChannelWrite, spanNamePrefix) {
      delegate.write(srcs, offset, length)
    }

  override fun write(src: ByteBuffer?, position: Long): Int =
    Measurer.measure(Measurer.Operation.fileChannelWrite, spanNamePrefix) {
      delegate.write(src, position)
    }

  override fun force(metaData: Boolean) {
    Measurer.measure(Measurer.Operation.fileChannelForce, spanNamePrefix) {
      delegate.force(metaData)
    }
  }

  override fun transferTo(position: Long, count: Long, target: WritableByteChannel?): Long =
    Measurer.measure(Measurer.Operation.fileChannelTransferTo, spanNamePrefix) {
      delegate.transferTo(position, count, target)
    }

  override fun transferFrom(src: ReadableByteChannel?, position: Long, count: Long): Long =
    Measurer.measure(Measurer.Operation.fileChannelTransferFrom, spanNamePrefix) {
      delegate.transferFrom(src, position, count)
    }

  override fun map(mode: MapMode?, position: Long, size: Long): MappedByteBuffer? =
    Measurer.measure(Measurer.Operation.fileChannelMap, spanNamePrefix) {
      delegate.map(mode, position, size)
    }

  override fun lock(position: Long, size: Long, shared: Boolean): FileLock? =
    Measurer.measure(Measurer.Operation.fileChannelLock, spanNamePrefix) {
      delegate.lock(position, size, shared)
    }

  override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? =
    Measurer.measure(Measurer.Operation.fileChannelTryLock, spanNamePrefix) {
      delegate.tryLock(position, size, shared)
    }

  override fun implCloseChannel() {
    Measurer.measure(Measurer.Operation.fileChannelClose, spanNamePrefix) {
      implCloseChannelMethod.invoke(delegate)
    }
  }

  companion object {
    private val methodCache = ConcurrentHashMap<Class<*>, Method>()

    private fun getImplCloseChannelMethod(clazz: Class<*>): Method {
      return methodCache.computeIfAbsent(clazz) {
        var current: Class<*>? = it
        while (current != null) {
          try {
            val method = current.getDeclaredMethod("implCloseChannel")
            method.isAccessible = true
            return@computeIfAbsent method
          }
          catch (_: NoSuchMethodException) {
            current = current.superclass
          }
        }
        throw NoSuchMethodException("implCloseChannel not found in ${clazz.name}")
      }
    }
  }

  override fun read(dst: ByteBuffer?): Int =
    Measurer.measure(Measurer.Operation.fileChannelRead, spanNamePrefix) {
      delegate.read(dst)
    }

  override fun write(src: ByteBuffer?): Int =
    Measurer.measure(Measurer.Operation.fileChannelWrite, spanNamePrefix) {
      delegate.write(src)
    }

  override fun position(): Long =
    Measurer.measure(Measurer.Operation.fileChannelPosition, spanNamePrefix) {
      delegate.position()
    }

  override fun position(newPosition: Long): FileChannel =
    apply {
      Measurer.measure(Measurer.Operation.fileChannelNewPosition, spanNamePrefix) {
        delegate.position(newPosition)
      }
    }

  override fun size(): Long =
    Measurer.measure(Measurer.Operation.fileChannelSize, spanNamePrefix) {
      delegate.size()
    }

  override fun truncate(size: Long): FileChannel =
    apply {
      Measurer.measure(Measurer.Operation.fileChannelTruncate, spanNamePrefix) {
        delegate.truncate(size)
      }
    }
}