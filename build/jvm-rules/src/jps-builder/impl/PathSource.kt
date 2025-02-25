// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.jps.dependency.impl

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.NodeSource
import java.io.File

private fun pathToHashCode(path: String): Int = Hashing.xxh3_64().hashBytesToInt(path.encodeToByteArray())

@Suppress("unused")
internal class PathSource : NodeSource {
  private val path: String

  // used for PHM  - make sure that our hash code is good
  private val hashCode: Int

  constructor(relativePath: String) {
    this.path = if (File.separatorChar == '/') relativePath else relativePath.replace(File.separatorChar, '/')
    hashCode = pathToHashCode(this.path)
  }

  constructor(`in`: GraphDataInput) {
    path = `in`.readUTF()
    hashCode = pathToHashCode(path)
  }

  override fun write(out: GraphDataOutput) {
    out.writeUTF(path)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is PathSource && hashCode == other.hashCode && path == other.path
  }

  override fun hashCode(): Int = hashCode

  override fun toString() = path
}