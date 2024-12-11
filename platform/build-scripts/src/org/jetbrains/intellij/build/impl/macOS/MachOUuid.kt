// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.macOS

import org.jetbrains.intellij.build.BuildContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

/**
 * Patches UUID value in the Mach-O [executable] with the [newUuid].
 * Only single-arch 64-bit files are supported.
 */
internal class MachOUuid(private val executable: Path, private val newUuid: UUID, private val context: BuildContext) {
  private companion object {
    const val LC_UUID = 0x1b
  }

  fun patch() {
    Files.newByteChannel(executable, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
      var buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
      channel.read(buffer)
      buffer.flip()
      val magic = buffer.getInt()
      check(magic == -0x1120531) { "Not a valid 64-bit Mach-O file: 0x" + Integer.toHexString(magic) }
      buffer.clear()
      channel.position(16)
      channel.read(buffer)
      buffer.flip()
      val nCmds = buffer.getInt()
      buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
      channel.position(32)
      (0..<nCmds).forEach {
        buffer.clear()
        channel.read(buffer)
        buffer.flip()
        val cmd = buffer.getInt()
        val cmdSize = buffer.getInt()
        if (cmd == LC_UUID) {
          buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
          channel.read(buffer)
          buffer.flip()
          val msb = buffer.getLong()
          val lsb = buffer.getLong()
          context.messages.info("current UUID of $executable: ${UUID(msb, lsb)}")
          buffer.clear()
          buffer.putLong(newUuid.mostSignificantBits)
          buffer.putLong(newUuid.leastSignificantBits)
          buffer.flip()
          channel.position(channel.position() - 16)
          channel.write(buffer)
          context.messages.info("new UUID of $executable: $newUuid")
          return
        }
        else {
          channel.position(channel.position() + cmdSize - 8)
        }
      }
      context.messages.error("LC_UUID not found in $executable")
    }
  }
}