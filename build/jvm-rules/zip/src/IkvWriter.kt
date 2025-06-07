// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class IkvWriter(private val channel: FileChannel) : AutoCloseable {
  private val indexBuilder = IkvIndexBuilder()
  private var position = 0L

  fun entry(key: Long, size: Int): IkvIndexEntry = IkvIndexEntry(longKey = key, offset = position, size = size)

  fun write(entry: IkvIndexEntry, data: ByteArray) {
    writeBuffer(Unpooled.wrappedBuffer(data))
    indexBuilder.add(entry)
  }

  @Suppress("DuplicatedCode")
  override fun close() {
    channel.use {
      val buf = ByteBuffer.allocate(indexBuilder.dataSize()).order(ByteOrder.LITTLE_ENDIAN)
      indexBuilder.write(buf)
      buf.flip()
      writeBuffer(Unpooled.wrappedBuffer(buf))
    }
  }

  private fun writeBuffer(value: ByteBuf) {
    position = writeToFileChannelFully(channel, position, value)
  }
}
