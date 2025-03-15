// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.jps.dependency.impl

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.bazel.jvm.jps.storage.IntLong
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.NodeSource
import java.io.File

private val isForwardSlash = File.separatorChar == '/'

class PathSource : NodeSource {
  private val path: String
  @JvmField val pathHash: IntLong

  constructor(relativePath: String) {
    path = if (isForwardSlash) relativePath else relativePath.replace(File.separatorChar, '/')
    val byteArray = path.encodeToByteArray()
    // Sort by package - ensure that data is grouped on disk to ensure cache locality and reducing loading a lot of random pages.
    // Using of `int` may lead to collision, but it is ok.
    val lastSlashIndex = lastIndexOf(byteArray, '/'.code.toByte())
    val pathFullHash = Hashing.xxh3_64().hashBytesToLong(byteArray)
    if (lastSlashIndex < 0) {
      pathHash = IntLong(0, pathFullHash)
    }
    else {
      pathHash = IntLong(Hashing.xxh3_64().hashBytesToInt(byteArray, 0, lastSlashIndex), pathFullHash)
    }
  }

  override fun write(out: GraphDataOutput) {
    throw UnsupportedOperationException("Not used for a new store")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is PathSource && pathHash == other.pathHash && path == other.path
  }

  override fun hashCode(): Int = pathHash.second.toInt()

  override fun toString() = path
}

private fun lastIndexOf(array: ByteArray, value: Byte): Int {
  var i = array.size - 1

  // process in chunks of 8 (loop unrolling)
  while (i >= 7) {
    if (array[i] == value) return i
    if (array[i - 1] == value) return i - 1
    if (array[i - 2] == value) return i - 2
    if (array[i - 3] == value) return i - 3
    if (array[i - 4] == value) return i - 4
    if (array[i - 5] == value) return i - 5
    if (array[i - 6] == value) return i - 6
    if (array[i - 7] == value) return i - 7
    i -= 8
  }

  // handle the remaining elements
  while (i >= 0) {
    if (array[i] == value) return i
    i--
  }
  return -1
}