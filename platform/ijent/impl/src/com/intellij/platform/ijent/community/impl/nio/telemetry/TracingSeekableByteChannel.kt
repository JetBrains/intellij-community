// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio.telemetry

import com.intellij.platform.ijent.community.impl.nio.telemetry.Measurer.Operation.*
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

internal class TracingSeekableByteChannel(
  private val provider: TracingFileSystemProvider,
  private val delegate: SeekableByteChannel,
) : SeekableByteChannel {
  override fun close() {
    Measurer.measure(seekableByteChannelClose) {
      delegate.close()
    }
  }

  override fun isOpen(): Boolean =
    delegate.isOpen

  override fun read(dst: ByteBuffer?): Int =
    Measurer.measure(seekableByteChannelRead) {
      delegate.read(dst)
    }

  override fun write(src: ByteBuffer?): Int =
    Measurer.measure(seekableByteChannelWrite) {
      delegate.write(src)
    }

  override fun position(): Long =
    Measurer.measure(seekableByteChannelPosition) {
      delegate.position()
    }

  override fun position(newPosition: Long): SeekableByteChannel =
    TracingSeekableByteChannel(
      provider,
      Measurer.measure(seekableByteChannelNewPosition) {
        delegate.position(newPosition)
      }
    )

  override fun size(): Long =
    Measurer.measure(seekableByteChannelSize) {
      delegate.size()
    }

  override fun truncate(size: Long): SeekableByteChannel =
    Measurer.measure(seekableByteChannelTruncate) {
      delegate.truncate(size)
    }
}
