// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.jps.dependency.impl

import org.jetbrains.jps.dependency.GraphDataInput
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.NodeSource
import java.io.File

private val isForwardSlash = File.separatorChar == '/'

class PathSource : NodeSource {
  private val path: String

  constructor(relativePath: String) {
    this.path = if (isForwardSlash) relativePath else relativePath.replace(File.separatorChar, '/')
  }

  constructor(`in`: GraphDataInput) {
    path = `in`.readUTF()
  }

  override fun write(out: GraphDataOutput) {
    out.writeUTF(path)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is PathSource && path == other.path
  }

  override fun hashCode(): Int = path.hashCode()

  override fun toString() = path
}