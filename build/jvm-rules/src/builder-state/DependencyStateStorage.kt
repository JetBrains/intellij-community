// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.state

import androidx.collection.ObjectList
import androidx.collection.ScatterMap
import java.nio.file.Path

// for now, ADDED or DELETED not possible - in configureClasspath we compute DEPENDENCY_PATH_LIST,
// so, if a dependency list is changed, then we perform rebuild
enum class DependencyState {
  CHANGED, ADDED, DELETED
}

fun isDependencyTracked(file: Path): Boolean = isDependencyTracked(file.toString())

fun isDependencyTracked(path: String): Boolean = path.endsWith(".abi.jar")

// do not include state in equals/hashCode - DependencyDescriptor is used as a key for Caffeine cache
data class DependencyDescriptor(
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

class DependencyStateStorage(
  @JvmField val trackableDependencyFiles: ObjectList<Path>,
  @JvmField val dependencyFileToDigest: ScatterMap<Path, ByteArray>,
)