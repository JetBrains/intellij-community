// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs.telemetry

import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

internal class TracingSeekableByteChannel(
  private val provider: TracingFileSystemProvider,
  private val delegate: SeekableByteChannel,
) : SeekableByteChannel {
  override fun close() {
    Measurer.measure(Measurer.Operation.seekableByteChannelClose) {
      delegate.close()
    }
  }

  override fun isOpen(): Boolean =
    delegate.isOpen

  override fun read(dst: ByteBuffer?): Int =
    Measurer.measure(Measurer.Operation.seekableByteChannelRead) {
      delegate.read(dst)
    }

  override fun write(src: ByteBuffer?): Int =
    Measurer.measure(Measurer.Operation.seekableByteChannelWrite) {
      delegate.write(src)
    }

  override fun position(): Long =
    Measurer.measure(Measurer.Operation.seekableByteChannelPosition) {
      delegate.position()
    }

  override fun position(newPosition: Long): SeekableByteChannel =
    TracingSeekableByteChannel(
      provider,
      Measurer.measure(Measurer.Operation.seekableByteChannelNewPosition) {
        delegate.position(newPosition)
      }
    )

  override fun size(): Long =
    Measurer.measure(Measurer.Operation.seekableByteChannelSize) {
      delegate.size()
    }

  override fun truncate(size: Long): SeekableByteChannel =
    Measurer.measure(Measurer.Operation.seekableByteChannelTruncate) {
      delegate.truncate(size)
    }
}
