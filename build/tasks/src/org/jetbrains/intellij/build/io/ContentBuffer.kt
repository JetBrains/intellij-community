// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import java.io.EOFException
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel

internal fun writeToChannelFully(channel: GatheringByteChannel, buffer: ByteBuf): Int {
  val length = buffer.readableBytes()
  var toWrite = length
  while (toWrite > 0) {
    val n = buffer.readBytes(channel, toWrite)
    if (n < 0) {
      throw EOFException("Unexpected end of file while writing to Channel")
    }
    toWrite -= n
  }
  return length
}

internal fun writeToFileChannelFully(channel: FileChannel, position: Long, buffer: ByteBuf): Int {
  val length = buffer.readableBytes()
  var toWrite = length
  var currentPosition = position
  while (toWrite > 0) {
    val n = buffer.readBytes(channel, currentPosition, toWrite)
    if (n < 0) {
      throw EOFException("Unexpected end of file while writing to FileChannel")
    }
    toWrite -= n
    currentPosition += n
  }
  return length
}

inline fun <T> ByteBuf.use(block: (ByteBuf) -> T): T {
  try {
    return block(this)
  }
  finally {
    release()
  }
}