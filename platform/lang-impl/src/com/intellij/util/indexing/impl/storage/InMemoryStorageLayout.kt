// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.InputMapExternalizer
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.ForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor
import com.intellij.util.indexing.memory.InMemoryForwardIndex
import com.intellij.util.indexing.memory.InMemoryIndexStorage
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout

class InMemoryStorageLayoutProvider : FileBasedIndexLayoutProvider {
  override fun <K, V> getLayout(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorageLayout<K, V> {
    return InMemoryStorageLayout(extension)
  }
}

class InMemoryStorageLayout<K, V>(private val myExtension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
  override fun openIndexStorage(): IndexStorage<K, V> {
    return InMemoryIndexStorage(myExtension.keyDescriptor)
  }

  override fun openForwardIndex(): ForwardIndex {
    return InMemoryForwardIndex()
  }

  override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
    return MapForwardIndexAccessor(InputMapExternalizer(myExtension))
  }

  override fun clearIndexData() = Unit
}