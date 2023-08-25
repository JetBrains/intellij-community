// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.*
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider.STORAGE_LAYOUT_EP_NAME
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.StorageLockContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object DefaultIndexStorageLayout {
  private val log = logger<DefaultIndexStorageLayout>()
  private val forcedLayout: String? = System.getProperty("idea.index.storage.forced.layout")

  @JvmStatic
  @Throws(IOException::class)
  fun <Key, Value> getLayout(indexExtension: FileBasedIndexExtension<Key, Value>): VfsAwareIndexStorageLayout<Key, Value> {
    val layoutEP = indexLayout
    if (layoutEP != null) {
      log.info("Layout '${layoutEP.id}' will be used to for '${indexExtension.name}' index")
      return layoutEP.layoutProvider.getLayout(indexExtension)
    }
    if (FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return InMemoryStorageLayout(indexExtension)
    }
    if (indexExtension is SingleEntryFileBasedIndexExtension<*>) {
      return SingleEntryStorageLayout(indexExtension as FileBasedIndexExtension<Key, Value>)
    }
    return DefaultStorageLayout(indexExtension)
  }

  @JvmStatic
  val usedLayoutId: String?
    get() = indexLayout?.id

  val availableLayouts : List<FileBasedIndexLayoutProviderBean> get() {
    return STORAGE_LAYOUT_EP_NAME.extensionList.filter { it.layoutProvider.isSupported }
  }

  private val indexLayout get() = forcedLayoutEP ?: FileBasedIndexLayoutSettings.getUsedLayout()

  private val forcedLayoutEP: FileBasedIndexLayoutProviderBean?
    get() {
      val layout = forcedLayout ?: return null
      return availableLayouts.find { it.id == layout }
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
  fun <K, V> createIndexStorage(extension: FileBasedIndexExtension<K, V>, storageLockContext: StorageLockContext): VfsAwareIndexStorage<K, V> {
    val storageFile = IndexInfrastructure.getStorageFile(extension.name)
    return object : VfsAwareMapIndexStorage<K, V>(
      storageFile,
      extension.keyDescriptor,
      extension.valueExternalizer,
      extension.cacheSize,
      extension.keyIsUniqueForIndexedFile(),
      extension.traceKeyHashToVirtualFileMapping(),
      extension.enableWal()
    ) {
      override fun initMapAndCache() {
        assert(PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get() == null)
        PagedFileStorage.THREAD_LOCAL_STORAGE_LOCK_CONTEXT.set(storageLockContext)
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
    private val storageLockContext = newStorageLockContext()

    @Throws(IOException::class)
    override fun openIndexStorage(): IndexStorage<K, V> {
      return createIndexStorage(extension, storageLockContext)
    }

    @Throws(IOException::class)
    override fun openForwardIndex(): ForwardIndex {
      val indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(extension.name)
      return PersistentMapBasedForwardIndex(indexStorageFile,
                                            false,
                                            false,
                                            storageLockContext)
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return MapForwardIndexAccessor(InputMapExternalizer(extension))
    }

    override fun clearIndexData() {
      log.info("Clearing storage data for: $extension")
      deleteIndexDirectory(extension)
    }
  }

  class SingleEntryStorageLayout<K, V> internal constructor(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    private val storageLockContext = newStorageLockContext()

    @Throws(IOException::class)
    override fun openIndexStorage(): IndexStorage<K, V> {
      return createIndexStorage(extension, storageLockContext)
    }

    override fun openForwardIndex(): ForwardIndex {
      return EmptyForwardIndex()
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return SingleEntryIndexForwardIndexAccessor(extension as IndexExtension<Int, V, *>) as ForwardIndexAccessor<K, V>
    }

    override fun clearIndexData() {
      deleteIndexDirectory(extension)
    }
  }

  private fun deleteIndexDirectory(extension: FileBasedIndexExtension<*, *>) {
    FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(extension.name).toFile())
  }

  private fun newStorageLockContext(): StorageLockContext {
    return StorageLockContext(false, true)
  }
}