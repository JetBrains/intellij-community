// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.fake

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.ValueContainerImpl
import com.intellij.util.indexing.impl.forward.EmptyForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor
import com.intellij.util.indexing.impl.storage.defaultMapExternalizerFor
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * 'Fake' index storage implementation doesn't store anything -- so the indexes are always effectively empty.
 *
 * It is intended to be used as a baseline in benchmarks, to compare actual implementations against it, and see the cost
 * of IO in relation to other overheads along the road.
 * [com.intellij.util.indexing.memory.InMemoryIndexStorage] is another such 'baseline' impl.
 *
 * The difference is that InMemory is a full-fledged impl, hence it is more costly to use -- it is not a zero-cost.
 * I.e. it consumes A LOT of heap if used in any real-life scenario -- typical indexes size for IntelliJ project
 * is few Gb, this is approximately how much heap it costs to use InMemory indexes in that scenario.
 * 'Fake' implementation costs nothing, but it is not a 'correct' implementation: i.e. [IndexStorage] contract
 * assumes that if you [IndexStorage.addValue] something -- this something could be read back. But [FakeIndexStorage]
 * doesn't adhere to it -- everything you add to it just disappears. Anyway, this is not important in some tests|benchmark-like
 * scenarios, there we test only one particular aspect of index storages functionality -- which is why this class is created
 */
@Internal
class FakeStorageLayoutProvider : FileBasedIndexLayoutProvider {
  override fun <K, V> getLayout(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorageLayout<K, V> {
    return FakeStorageLayout(extension)
  }

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean = true
}

internal class FakeStorageLayout<K, V>(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
  override fun openIndexStorage(): IndexStorage<K, V> {
    return FakeIndexStorage()
  }

  override fun openForwardIndex(): ForwardIndex {
    return EmptyForwardIndex.INSTANCE
  }

  override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
    return MapForwardIndexAccessor(defaultMapExternalizerFor(extension))
  }

  override fun clearIndexData() {}
}

internal class FakeIndexStorage<K, V> : VfsAwareIndexStorage<K, V> {
  override fun processKeys(processor: Processor<in K>, scope: GlobalSearchScope?, idFilter: IdFilter?): Boolean = true

  override fun read(key: K): ValueContainer<V> = ValueContainerImpl.createNewValueContainer()

  override fun flush() = Unit

  override fun close() = Unit

  override fun addValue(key: K, inputId: Int, value: V) = Unit

  override fun removeAllValues(key: K & Any, inputId: Int) = Unit

  override fun clear() = Unit

  override fun clearCaches() = Unit

  override fun isDirty(): Boolean = false

  override fun isClosed(): Boolean = false

  override fun keysCountApproximately(): Int = 0
}
