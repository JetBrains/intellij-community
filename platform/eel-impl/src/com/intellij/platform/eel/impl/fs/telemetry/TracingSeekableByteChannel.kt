// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

internal class TracingSeekableByteChannel(
  private val delegate: SeekableByteChannel,
  private val spanNamePrefix: String
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
