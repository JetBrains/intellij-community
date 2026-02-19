// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import org.jetbrains.intellij.build.http2Client.READ_OPERATION
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.security.MessageDigest

private val sharedDigest = MessageDigest.getInstance("SHA-256", java.security.Security.getProvider("SUN"))
internal fun sha256() = sharedDigest.clone() as MessageDigest

internal fun computeHash(file: Path): String {
  val messageDigest = sha256()
  FileChannel.open(file, READ_OPERATION).use { channel ->
    val fileSize = channel.size()
    // java message digest doesn't support native buffer (copies to a heap byte array in any case)
    val bufferSize = 256 * 1024
    val buffer = ByteBuffer.allocate(bufferSize)
    var offset = 0L
    var readBytes: Int
    while (offset < fileSize) {
      buffer.clear()
      readBytes = channel.read(buffer, offset)
      if (readBytes <= 0) {
        break
      }

      messageDigest.update(buffer.array(), 0, readBytes)
      offset += readBytes
    }
  }
  return digestToString(messageDigest)
}

internal fun digestToString(digest: MessageDigest): String = BigInteger(1, digest.digest()).toString(36) + "z"