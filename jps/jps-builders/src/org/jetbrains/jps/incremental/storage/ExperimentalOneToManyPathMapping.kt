// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import com.dynatrace.hash4j.hashing.Hashing
import org.h2.mvstore.type.DataType
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.dataTypes.LongPairKeyDataType
import org.jetbrains.jps.incremental.storage.dataTypes.StringListDataType
import java.io.IOException
import kotlin.Throws

internal interface OneToManyPathMapping : StorageOwner {
  @Throws(IOException::class)
  fun getState(path: String): Collection<String>?

  @Throws(IOException::class)
  fun update(path: String, outPaths: List<String>)

  @Throws(IOException::class)
  fun remove(path: String)
}

internal class ExperimentalOneToManyPathMapping(
  mapName: String,
  storageManager: StorageManager,
  private val relativizer: PathRelativizerService,
) : OneToManyPathMapping,
    StorageOwnerByMap<LongArray, Array<String>>(
      mapName = mapName,
      storageManager = storageManager,
      keyType = LongPairKeyDataType,
      valueType = StringListDataType,
    ) {

  private fun getKey(path: String): LongArray {
    val stringKey = relativizer.toRelative(path).toByteArray()
    return longArrayOf(Hashing.xxh3_64().hashBytesToLong(stringKey), Hashing.komihash5_0().hashBytesToLong(stringKey))
  }

  @Suppress("ReplaceGetOrSet")
  override fun getState(path: String): Collection<String>? {
    val key = getKey(path)
    val list = mapHandle.map.get(key) ?: return null
    return Array<String>(list.size) { relativizer.toFull(list.get(it)) }.asList()
  }

  override fun update(path: String, outPaths: List<String>) {
    val key = getKey(path)
    if (outPaths.isEmpty()) {
      mapHandle.map.remove(key)
    }
    else {
      val listWithRelativePaths = Array(outPaths.size) {
        relativizer.toRelative(outPaths.get(it))
      }
      mapHandle.map.put(key, listWithRelativePaths)
    }
  }

  override fun remove(path: String) {
    mapHandle.map.remove(getKey(path))
  }
}

internal sealed class StorageOwnerByMap<K : Any, V : Any>(
  mapName: String,
  storageManager: StorageManager,
  keyType: DataType<K>,
  valueType: DataType<V>,
) : StorageOwner {
  @JvmField
  protected val mapHandle = storageManager.openMap(mapName, keyType, valueType)

  final override fun flush(memoryCachesOnly: Boolean) {
    if (memoryCachesOnly) {
      // set again to force to clear the cache (in kb)
      mapHandle.map.store.cacheSize = MV_STORE_CACHE_SIZE_IN_MB * 1024
    }
    else {
      mapHandle.tryCommit()
    }
  }

  final override fun clean() {
    mapHandle.map.clear()
  }

  final override fun close() {
    mapHandle.release()
  }
}