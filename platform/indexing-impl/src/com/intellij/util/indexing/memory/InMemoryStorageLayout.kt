// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.memory

import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.ForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor
import com.intellij.util.indexing.impl.storage.defaultMapExternalizerFor
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class InMemoryStorageLayoutProvider : FileBasedIndexLayoutProvider {
  override fun <K, V> getLayout(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorageLayout<K, V> {
    return InMemoryStorageLayout(extension)
  }

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean {
    return FileBasedIndex.USE_IN_MEMORY_INDEX
  }
}

@Internal
class InMemoryStorageLayout<K, V>(private val myExtension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
  override fun openIndexStorage(): IndexStorage<K, V> {
    return InMemoryIndexStorage(myExtension.keyDescriptor)
  }

  override fun openForwardIndex(): ForwardIndex {
    return InMemoryForwardIndex()
  }

  override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
    return MapForwardIndexAccessor(defaultMapExternalizerFor(myExtension))
  }

  override fun clearIndexData() {}
}