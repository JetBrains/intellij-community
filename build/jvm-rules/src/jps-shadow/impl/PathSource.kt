// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.jps.dependency.impl

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.bazel.jvm.mvStore.IntLong
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
    val lastSlashIndex = byteArray.lastIndexOf('/'.code.toByte())
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