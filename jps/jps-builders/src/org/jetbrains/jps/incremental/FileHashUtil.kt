// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtilRt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Internal
@Throws(IOException::class)
fun getFileHash(file: Path): Long = getFileHash(file, Hashing.komihash5_0().hashStream())

@Internal
@Throws(IOException::class)
fun getFileHash(file: Path, hash: HashStream64): Long {
  FileChannel.open(file, StandardOpenOption.READ).use { channel ->
    val fileSize = channel.size()
    val buffer = ByteBuffer.allocate(256 * 1024)
    var offset = 0L
    var readBytes: Int
    while (offset < fileSize) {
      buffer.clear()
      readBytes = channel.read(buffer, offset)
      if (readBytes <= 0) {
        break
      }

      hash.putBytes(buffer.array(), 0, readBytes)
      offset += readBytes
    }
    hash.putLong(fileSize)
    return hash.asLong
  }
}

/** path must be absolute ([Path.toAbsolutePath]), normalized ([Path.normalize]) and system-independent */
internal fun normalizedPathHashCode(path: String, hash: HashStream64) {
  if (path.isEmpty()) {
    hash.putInt(0)
    return
  }

  val length = path.length
  if (ProjectStamps.PORTABLE_CACHES || SystemInfoRt.isFileSystemCaseSensitive) {
    hash.putChars(path)
  }
  else {
    for (offset in 0 until length) {
      hash.putChar(StringUtilRt.toLowerCase(path[offset]))
    }
  }

  hash.putInt(length)
}