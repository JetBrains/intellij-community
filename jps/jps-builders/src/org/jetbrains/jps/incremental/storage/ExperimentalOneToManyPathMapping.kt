// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.dataTypes.stringTo128BitHash

@ApiStatus.Internal
open class ExperimentalOneToManyPathMapping(
  @JvmField protected val mapHandle: MapHandle<LongArray, Array<String>>,
  @JvmField internal val relativizer: PathRelativizerService,
  private val valueOffset: Int = 0,
) : OneToManyPathMapping {
  protected fun getKey(path: String): LongArray = stringTo128BitHash(relativizer.toRelative(path))

  @Suppress("ReplaceGetOrSet")
  final override fun getOutputs(path: String): List<String>? {
    val key = getKey(path)
    val list = mapHandle.map.get(key) ?: return null
    return Array<String>(list.size - valueOffset) { relativizer.toFull(list.get(it + valueOffset)) }.asList()
  }

  protected fun normalizeOutputPaths(outPaths: List<String>, relativeSourcePath: String?): Array<String>? {
    return when {
      outPaths.isEmpty() -> null
      relativeSourcePath != null -> {
        Array(outPaths.size + 1) {
          if (it == 0) relativeSourcePath else relativizer.toRelative(outPaths.get(it - 1))
        }
      }
      else -> {
        Array(outPaths.size) {
          relativizer.toRelative(outPaths.get(it))
        }
      }
    }
  }

  override fun setOutputs(path: String, outPaths: List<String>) {
    val relativeSourcePath = relativizer.toRelative(path)
    val key = stringTo128BitHash(relativeSourcePath)
    val normalizeOutputPaths = normalizeOutputPaths(outPaths, null)
    if (normalizeOutputPaths == null) {
      mapHandle.map.remove(key)
    }
    else {
      mapHandle.map.put(key, normalizeOutputPaths)
    }
  }

  final override fun remove(path: String) {
    mapHandle.map.remove(getKey(path))
  }
}