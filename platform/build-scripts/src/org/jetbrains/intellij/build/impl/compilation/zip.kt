// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.util.lang.HashMapZipFile
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.function.IntFunction

private val OVERWRITE_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

internal fun unpackArchiveUsingNettyByteBufferPool(archiveFile: Path, outDir: Path, isCompressed: Boolean) {
  Files.createDirectories(outDir)
  HashMapZipFile.load(archiveFile).use { zipFile ->
    val createdDirs = HashSet<Path>()
    createdDirs.add(outDir)
    for (entry in zipFile.entries) {
      if (entry.isDirectory || entry.name == INDEX_FILENAME) {
        continue
      }

      val file = outDir.resolve(entry.name)
      val parent = file.parent
      if (createdDirs.add(parent)) {
        Files.createDirectories(parent)
      }

      FileChannel.open(file, OVERWRITE_OPERATION).use { channel ->
        if (isCompressed) {
          // we use netty buffer as a part of compilation cache/parts tasks - reuse it
          var nettyBuffer: ByteBuf? = null
          try {
            val byteBuffer = entry.getByteBuffer(
              zipFile,
              IntFunction { size ->
                val buffer = PooledByteBufAllocator.DEFAULT.directBuffer(size)
                nettyBuffer = buffer
                buffer.nioBuffer(0, size)
              })
            channel.write(byteBuffer, 0)
          }
          finally {
            nettyBuffer?.release()
          }
        }
        else {
          channel.write(entry.getByteBuffer(zipFile, null))
        }
      }
    }
  }
}