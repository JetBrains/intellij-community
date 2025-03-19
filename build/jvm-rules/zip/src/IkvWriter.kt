// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

fun sizeAwareIkvWriter(file: Path): IkvWriter {
  return IkvWriter(channel = FileChannel.open(file, EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)))
}

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
      val buf = Unpooled.buffer()
      indexBuilder.write(buf)
      writeBuffer(buf)
    }
  }

  private fun writeBuffer(value: ByteBuf) {
    position += writeToFileChannelFully(channel, position, value)
  }
}
