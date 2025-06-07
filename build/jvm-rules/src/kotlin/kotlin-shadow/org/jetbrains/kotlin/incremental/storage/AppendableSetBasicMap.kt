// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.kotlin.incremental.storage

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentHashSet
import org.h2.mvstore.MVMap
import org.h2.mvstore.type.DataType
import org.jetbrains.bazel.jvm.mvStore.AddValueToSetDecisionMaker
import org.jetbrains.bazel.jvm.mvStore.AddValuesToSetDecisionMaker
import org.jetbrains.bazel.jvm.mvStore.enumeratedIntSetValueDataType
import org.jetbrains.bazel.jvm.mvStore.enumeratedStringSetValueDataType
import org.jetbrains.bazel.jvm.mvStore.kotlin.DataExternalizerSetValueDataType
import org.jetbrains.bazel.jvm.mvStore.kotlin.createKeyDataType
import org.jetbrains.bazel.jvm.mvStore.kotlin.guessKeyDataType
import org.jetbrains.kotlin.incremental.IncrementalCompilationContext
import org.jetbrains.bazel.jvm.mvStore.mvStoreMapFactoryExposer
import java.io.File

private class BazelAppendableStorage<K : Any, E : Any>(
  storageFile: File,
  keyType: DataType<K>,
  elementExternalizer: DataExternalizer<E>,
  icContext: IncrementalCompilationContext,
) {
  val mapFactory = mvStoreMapFactoryExposer.get()!!

  @JvmField
  val map: MVMap<K, PersistentSet<E>> = run {
    val valueDataType: DataType<PersistentSet<E>> = createKeyDataType(elementExternalizer, icContext) {
      enumeratedStringSetValueDataType(mapFactory.getStringEnumerator(), it)
    }
      ?: run {
        @Suppress("UNCHECKED_CAST")
        if (elementExternalizer == IntExternalizer) {
          enumeratedIntSetValueDataType() as DataType<PersistentSet<E>>
        }
        else {
          DataExternalizerSetValueDataType(elementExternalizer)
        }
      }
    val mapBuilder = MVMap.Builder<K, PersistentSet<E>>()
      .keyType(keyType)
      .valueType(valueDataType)
    mapFactory.openMap(mapName = storageFile.name, mapBuilder = mapBuilder)
  }

  fun append(key: K, elements: Collection<E>) {
    map.operate(key, null, AddValuesToSetDecisionMaker(elements))
  }
}

@Suppress("unused")
abstract class AppendableBasicMap<K : Any, E : Any> private constructor(
  private val map: BazelAppendableStorage<K, E>,
) : BasicMap<K, Collection<E>>, AppendablePersistentStorage<K, E> {
  constructor(
    storageFile: File,
    keyDescriptor: KeyDescriptor<K>,
    elementExternalizer: DataExternalizer<E>,
    icContext: IncrementalCompilationContext,
  ) : this(BazelAppendableStorage(
    storageFile = storageFile,
    keyType = guessKeyDataType(keyDescriptor, icContext),
    elementExternalizer = elementExternalizer,
    icContext = icContext,
  ))

  override fun append(key: K, elements: Collection<E>) {
    map.append(key, elements)
  }

  override val storageFile: File
    get() = throw UnsupportedOperationException("storageFile is not supported")

  override val keys: Set<K>
    get() = map.map.keys

  override fun contains(key: K): Boolean {
    return map.map.containsKey(key)
  }

  override fun get(key: K): Collection<E>? = map.map.get(key)

  override fun set(key: K, value: Collection<E>) {
    map.map.put(key, value.toPersistentHashSet())
  }

  override fun remove(key: K) {
    map.map.remove(key)
  }

  override fun flush() {
  }

  override fun close() {
  }

  override fun clean() {
    throw UnsupportedOperationException("clean is not supported")
  }
}

@Suppress("unused")
abstract class AppendableSetBasicMap<K : Any, E : Any>(
  storageFile: File,
  keyDescriptor: KeyDescriptor<K>,
  elementExternalizer: DataExternalizer<E>,
  icContext: IncrementalCompilationContext,
) : PersistentStorage<K, Set<E>>, BasicMap<K, Set<E>> {
  private val map = BazelAppendableStorage(
    storageFile = storageFile,
    keyType = guessKeyDataType(keyDescriptor, icContext),
    elementExternalizer = elementExternalizer,
    icContext = icContext,
  )

  override val storageFile: File
    get() = throw UnsupportedOperationException("storageFile is not supported")

  override val keys: Set<K>
    get() = map.map.keys

  override fun contains(key: K): Boolean = map.map.containsKey(key)

  override fun get(key: K): Set<E>? = map.map.get(key)

  override fun set(key: K, value: Set<E>) {
    map.map.put(key, value.toPersistentHashSet())
  }

  override fun remove(key: K) {
    map.map.remove(key)
  }

  fun append(key: K, element: E) {
    map.map.operate(key, null, AddValueToSetDecisionMaker(element))
  }

  fun append(key: K, elements: Set<E>) {
    map.append(key, elements)
  }

  override fun clean() {
    map.map.clear()
  }

  override fun flush() {
  }

  override fun close() {
  }
}