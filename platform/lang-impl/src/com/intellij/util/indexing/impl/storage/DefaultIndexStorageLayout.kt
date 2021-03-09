// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.*
import com.intellij.util.indexing.snapshot.SnapshotInputMappings
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.io.IOUtil
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.StorageLockContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object DefaultIndexStorageLayout {
  private val forcedLayout: String? = System.getProperty("idea.index.storage.forced.layout")
  private val contentLessIndexLock: StorageLockContext = StorageLockContext(true, false, true)

  @JvmStatic
  @Throws(IOException::class)
  fun <Key, Value> getLayout(indexExtension: FileBasedIndexExtension<Key, Value>,
                             contentHashEnumeratorReopen: Boolean): VfsAwareIndexStorageLayout<Key, Value> {
    val layoutEP = getForcedLayoutEP() ?: FileBasedIndexLayoutSettings.getUsedLayout()
    if (layoutEP != null) {
      return layoutEP.layoutProvider.getLayout(indexExtension)
    }
    if (FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return InMemoryStorageLayout(indexExtension)
    }
    if (indexExtension is SingleEntryFileBasedIndexExtension<*>) {
      return SingleEntryStorageLayout(indexExtension as FileBasedIndexExtension<Key, Value>)
    }
    return if (VfsAwareMapReduceIndex.hasSnapshotMapping(indexExtension)) {
      SnapshotMappingsStorageLayout(indexExtension, contentHashEnumeratorReopen)
    }
    else DefaultStorageLayout(indexExtension)
  }

  private fun getForcedLayoutEP(): FileBasedIndexLayoutProviderBean? {
    val layout = forcedLayout ?: return null
    return FileBasedIndexLayoutProvider.STORAGE_LAYOUT_EP_NAME.extensions.find { it.id == layout }
           ?: throw IllegalStateException("Can't find index storage layout for id = $layout")
  }

  @ApiStatus.Internal
  private fun <Key, Value> getForwardIndexAccessor(indexExtension: IndexExtension<Key, Value, *>): AbstractMapForwardIndexAccessor<Key, Value, *> {
    return if (indexExtension !is SingleEntryFileBasedIndexExtension<*> || FileBasedIndex.USE_IN_MEMORY_INDEX) {
      MapForwardIndexAccessor(InputMapExternalizer(indexExtension))
    }
    else SingleEntryIndexForwardIndexAccessor(indexExtension as IndexExtension<Int, Any, *>) as AbstractMapForwardIndexAccessor<Key, Value, *>
  }

  @Throws(IOException::class)
  fun <K, V> createIndexStorage(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorage<K, V> {
    val storageFile = IndexInfrastructure.getStorageFile(extension.name)
    return object : VfsAwareMapIndexStorage<K, V>(
      storageFile,
      extension.keyDescriptor,
      extension.valueExternalizer,
      extension.cacheSize,
      extension.keyIsUniqueForIndexedFile(),
      extension.traceKeyHashToVirtualFileMapping()
    ) {
      override fun initMapAndCache() {
        assert(PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get() == null)
        if (!extension.dependsOnFileContent()) {
          PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.set(contentLessIndexLock)
        }
        try {
          super.initMapAndCache()
        }
        finally {
          PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.remove()
        }
      }
    }

  }

  class DefaultStorageLayout<K, V>(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    @Throws(IOException::class)
    override fun openIndexStorage(): IndexStorage<K, V> {
      return createIndexStorage(extension)
    }

    @Throws(IOException::class)
    override fun openForwardIndex(): ForwardIndex {
      val indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(extension.name)
      val storageLockContext = if (extension.dependsOnFileContent()) null else contentLessIndexLock
      return PersistentMapBasedForwardIndex(indexStorageFile,
                                            false,
                                            false,
                                            storageLockContext)
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return MapForwardIndexAccessor(InputMapExternalizer(extension))
    }

    override fun clearIndexData() {
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getIndexRootDir(extension.name))
    }
  }

  class SnapshotMappingsStorageLayout<K, V> internal constructor(private val extension: FileBasedIndexExtension<K, V>,
                                                                 contentHashEnumeratorReopen: Boolean) : VfsAwareIndexStorageLayout<K, V> {
    private val mySnapshotInputMappings: SnapshotInputMappings<K, V> by lazy(LazyThreadSafetyMode.NONE) {
      SnapshotInputMappings<K, V>(extension, getForwardIndexAccessor(extension))
    }

    private fun deleteIndexData() {
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getPersistentIndexRootDir(extension.name))
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getIndexRootDir(extension.name))
    }

    @Throws(IOException::class)
    override fun createOrClearSnapshotInputMappings(): SnapshotInputMappings<K, V> {
      return mySnapshotInputMappings
    }

    @Throws(IOException::class)
    override fun openIndexStorage(): IndexStorage<K, V> {
      mySnapshotInputMappings
      return createIndexStorage(extension)
    }

    @Throws(IOException::class)
    override fun openForwardIndex(): ForwardIndex {
      val storageFile = mySnapshotInputMappings.inputIndexStorageFile
      return IntMapForwardIndex(storageFile, true)
    }

    @Throws(IOException::class)
    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return mySnapshotInputMappings.forwardIndexAccessor
    }

    init {
      if (!contentHashEnumeratorReopen) {
        deleteIndexData()
      }
    }

    override fun clearIndexData() = deleteIndexData()
  }

  class SingleEntryStorageLayout<K, V> internal constructor(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    @Throws(IOException::class)
    override fun openIndexStorage(): IndexStorage<K, V> {
      return createIndexStorage(extension)
    }

    override fun openForwardIndex(): ForwardIndex {
      return EmptyForwardIndex()
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return SingleEntryIndexForwardIndexAccessor(extension as IndexExtension<Int, V, *>) as ForwardIndexAccessor<K, V>
    }

    override fun clearIndexData() {
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getIndexRootDir(extension.name))
    }
  }
}