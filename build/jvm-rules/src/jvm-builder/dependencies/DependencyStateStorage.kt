// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.dependencies

import java.nio.file.Path

// do not include state in equals/hashCode - DependencyDescriptor is used as a key for Caffeine cache
internal data class DependencyDescriptor(
  @JvmField val file: Path,
  // we use DependencyDescriptor as a key for cache of diff between old and new versions, so we must include oldDigest in equals/hashCode
  @JvmField val oldDigest: ByteArray?,
  @JvmField val digest: ByteArray?,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DependencyDescriptor) return false

    if (file != other.file) return false
    if (!digest.contentEquals(other.digest)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = file.hashCode()
    result = 31 * result + digest.contentHashCode()
    result = 31 * result + oldDigest.contentHashCode()
    return result
  }
}