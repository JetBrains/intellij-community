// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker.state

import java.nio.file.Path

data class SourceDescriptor(
  // absolute and normalized
  @JvmField val sourceFile: Path,
  @JvmField val digest: ByteArray,
  @JvmField var outputs: Array<String>,
  @JvmField var isChanged: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SourceDescriptor) return false

    if (sourceFile != other.sourceFile) return false
    if (!digest.contentEquals(other.digest)) return false
    if (!outputs.contentEquals(other.outputs)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = sourceFile.hashCode()
    result = 31 * result + (digest.contentHashCode())
    result = 31 * result + outputs.contentHashCode()
    return result
  }
}
