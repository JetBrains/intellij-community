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

  override fun setOutputs(path: Path, outPaths: List<Path>) {
    val normalizedOutputPaths = if (outPaths.isEmpty()) {
      null
    }
    else {
      Array(outPaths.size) {
        relativizer.toRelative(outPaths.get(it), valueKind)
      }
    }
    if (normalizedOutputPaths == null) {
      map.remove(getKey(path))
    }
    else {
      map.put(getKey(path), normalizedOutputPaths)
    }
  }

  final override fun remove(key: Path) {
    map.remove(getKey(key))
  }
}