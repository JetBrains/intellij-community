// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.kotlin.incremental.storage

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import org.h2.mvstore.type.DataType
import org.jetbrains.bazel.jvm.mvStore.ModernStringDataType
import org.jetbrains.bazel.jvm.mvStore.kotlin.BazelKotlinStorage
import org.jetbrains.bazel.jvm.mvStore.kotlin.bazelCreatePersistentStorage
import org.jetbrains.bazel.jvm.mvStore.kotlin.guessKeyDataType
import org.jetbrains.kotlin.incremental.IncrementalCompilationContext
import java.io.File

@Suppress("unused")
abstract class AbstractBasicMap<KEY : Any, VALUE : Any> internal constructor(
  @JvmField
  internal val map: BazelKotlinStorage<KEY, VALUE>,
) : PersistentStorage<KEY, VALUE>, BasicMap<KEY, VALUE> {
  @Suppress("UNCHECKED_CAST")
  constructor(
    storageFile: File,
    keyDescriptor: KeyDescriptor<KEY>,
    valueExternalizer: DataExternalizer<VALUE>,
    icContext: IncrementalCompilationContext,
  ) : this(
    map = bazelCreatePersistentStorage(
      storageFile = storageFile,
      keyType = guessKeyDataType(keyDescriptor, icContext) as DataType<KEY>,
      valueExternalizer = valueExternalizer,
      icContext = icContext,
    )
  )

  override val storageFile: File
    get() = throw UnsupportedOperationException("storageFile is not supported")

  override val keys: Set<KEY>
    get() = map.keys

  override fun contains(key: KEY): Boolean = map.contains(key)

  override fun get(key: KEY): VALUE? = map.get(key)

  override fun set(key: KEY, value: VALUE) {
    map.set(key, value)
  }

  override fun remove(key: KEY) {
    map.remove(key)
  }

  override fun clean() {
    map.map.clear()
  }

  override fun flush() {
  }

  override fun close() {
  }
}

@Suppress("unused")
abstract class BasicStringMap<VALUE : Any>(
  storageFile: File,
  valueExternalizer: DataExternalizer<VALUE>,
  icContext: IncrementalCompilationContext,
) : AbstractBasicMap<String, VALUE>(
  map = bazelCreatePersistentStorage(
    storageFile = storageFile,
    keyType = ModernStringDataType,
    valueExternalizer = valueExternalizer,
    icContext = icContext,
  )
) {
  protected val storage: PersistentStorage<String, VALUE> = map
}