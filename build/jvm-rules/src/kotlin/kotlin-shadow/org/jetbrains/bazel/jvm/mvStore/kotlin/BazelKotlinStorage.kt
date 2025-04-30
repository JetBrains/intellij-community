// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore.kotlin

import com.intellij.util.io.DataExternalizer
import org.h2.mvstore.MVMap
import org.h2.mvstore.type.DataType
import org.jetbrains.bazel.jvm.mvStore.EnumeratedStringDataType
import org.jetbrains.bazel.jvm.mvStore.ModernStringDataType
import org.jetbrains.kotlin.incremental.storage.PersistentStorage
import org.jetbrains.kotlin.incremental.storage.StringExternalizer
import org.jetbrains.bazel.jvm.mvStore.mvStoreMapFactoryExposer
import org.jetbrains.kotlin.incremental.IncrementalCompilationContext
import java.io.File

internal fun <KEY : Any, VALUE : Any> bazelCreatePersistentStorage(
  storageFile: File,
  keyType: DataType<KEY>,
  valueExternalizer: DataExternalizer<VALUE>,
  icContext: IncrementalCompilationContext,
): BazelKotlinStorage<KEY, VALUE> {
  val mapFactory = mvStoreMapFactoryExposer.get()!!

  @Suppress("UNCHECKED_CAST")
  val valueDataType = when {
    valueExternalizer === StringExternalizer -> ModernStringDataType as DataType<VALUE>
    valueExternalizer === icContext.fileDescriptorForSourceFiles || valueExternalizer === icContext.fileDescriptorForOutputFiles -> {
      // looks like `fileDescriptorForOutputFiles` is not used (InputsCache)
      EnumeratedStringDataType(
        stringEnumerator = mapFactory.getStringEnumerator(),
        externalizer = FileEnumeratedStringExternalizer(mapFactory.getOldPathRelativizer()),
      ) as DataType<VALUE>
    }
    else -> {
      createDataTypeAdapter(valueExternalizer)
    }
  }
  val mapBuilder = MVMap.Builder<KEY, VALUE>()
    .keyType(keyType)
    .valueType(valueDataType)
  val map = mapFactory.openMap(mapName = storageFile.name, mapBuilder = mapBuilder)
  return BazelKotlinStorage(map = map)
}

internal class BazelKotlinStorage<K : Any, V : Any>(
  @JvmField val map: MVMap<K, V>,
) : PersistentStorage<K, V> {
  override val storageFile: File
    get() = throw UnsupportedOperationException("storageFile is not supported")

  @get:Synchronized
  override val keys: Set<K>
    get() = map.keys

  @Synchronized
  override fun contains(key: K): Boolean {
    return map.containsKey(key)
  }

  @Synchronized
  override fun get(key: K): V? = map.get(key)

  @Synchronized
  override fun set(key: K, value: V) {
    map.put(key, value)
  }

  @Synchronized
  override fun remove(key: K) {
    map.remove(key)
  }

  override fun flush() {
  }

  override fun close() {
  }

  override fun clean() {
    throw UnsupportedOperationException("clean is not supported")
  }
}