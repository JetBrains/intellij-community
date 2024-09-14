// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import com.dynatrace.hash4j.hashing.Hashing
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.StringListDataType

@ApiStatus.Internal
open class ExperimentalOneToManyPathMapping protected constructor(
  @JvmField protected val mapHandle: MapHandle<LongArray, Array<String>>,
  @JvmField protected val relativizer: PathRelativizerService,
  private val valueOffset: Int = 0,
) : OneToManyPathMapping{
  constructor(
    mapName: String,
    storageManager: StorageManager,
    relativizer: PathRelativizerService,
  ) : this(storageManager.openMap(mapName, LongPairKeyDataType, StringListDataType), relativizer)

  protected fun getKey(path: String): LongArray = stringTo128BitHash(relativizer.toRelative(path))

  @Suppress("ReplaceGetOrSet")
  final override fun getOutputs(path: String): List<String>? {
    val key = getKey(path)
    val list = mapHandle.map.get(key) ?: return null
    return Array<String>(list.size - valueOffset) { relativizer.toFull(list.get(it + valueOffset)) }.asList()
  }

  final override fun setOutputs(path: String, outPaths: List<String>) {
    val relativeSourcePath = relativizer.toRelative(path)
    val key = stringTo128BitHash(relativeSourcePath)
    if (outPaths.isEmpty()) {
      mapHandle.map.remove(key)
    }
    else if (valueOffset == 1) {
      val listWithRelativePaths = Array(outPaths.size + 1) {
        if (it == 0) relativeSourcePath else relativizer.toRelative(outPaths.get(it - 1))
      }
      mapHandle.map.put(key, listWithRelativePaths)
    }
    else {
      val listWithRelativePaths = Array(outPaths.size) {
        relativizer.toRelative(outPaths.get(it))
      }
      mapHandle.map.put(key, listWithRelativePaths)
    }
  }

  final override fun remove(path: String) {
    mapHandle.map.remove(getKey(path))
  }
}

internal fun stringTo128BitHash(string: String): LongArray {
  val bytes = string.toByteArray()
  return longArrayOf(Hashing.xxh3_64().hashBytesToLong(bytes), Hashing.komihash5_0().hashBytesToLong(bytes))
}