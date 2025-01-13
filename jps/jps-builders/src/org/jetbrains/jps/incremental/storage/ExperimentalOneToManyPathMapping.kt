// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import org.h2.mvstore.MVMap
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.incremental.storage.dataTypes.stringTo128BitHash
import java.nio.file.Path

@Internal
open class ExperimentalOneToManyPathMapping(
  @JvmField val map: MVMap<LongArray, Array<String>>,
  @JvmField internal val relativizer: PathTypeAwareRelativizer,
  private val valueOffset: Int = 0,
  private val keyKind: RelativePathType,
  private val valueKind: RelativePathType,
) : OneToManyPathMapping {
  fun getKey(path: String): LongArray = stringTo128BitHash(relativizer.toRelative(path, keyKind))

  fun getKey(file: Path): LongArray = stringTo128BitHash(relativizer.toRelative(file, keyKind))

  final override fun getOutputs(path: String): List<String>? = doGetValuesByRawKey(getKey(path))

  final override fun getOutputs(file: Path): List<Path>? {
    val key = getKey(file)
    val list = map.get(key) ?: return null
    return Array<Path>(list.size - valueOffset) { relativizer.toAbsoluteFile(list.get(it + valueOffset), valueKind) }.asList()
  }

  private fun doGetValuesByRawKey(key: LongArray): List<String>? {
    val list = map.get(key) ?: return null
    return Array<String>(list.size - valueOffset) { relativizer.toAbsolute(list.get(it + valueOffset), valueKind) }.asList()
  }

  fun normalizeOutputPaths(outPaths: List<String>, relativeSourcePath: String?): Array<String>? {
    return when {
      outPaths.isEmpty() -> null
      relativeSourcePath != null -> {
        Array(outPaths.size + 1) {
          if (it == 0) relativeSourcePath else relativizer.toRelative(outPaths.get(it - 1), valueKind)
        }
      }
      else -> {
        Array(outPaths.size) {
          relativizer.toRelative(outPaths.get(it), valueKind)
        }
      }
    }
  }

  override fun setOutputs(path: String, outPaths: List<String>) {
    val normalizedOutputPaths = normalizeOutputPaths(outPaths, null)
    if (normalizedOutputPaths == null) {
      map.remove(getKey(path))
    }
    else {
      map.put(getKey(path), normalizedOutputPaths)
    }
  }

  final override fun remove(path: String) {
    map.remove(getKey(path))
  }
}