// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.*
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProvider.DefaultStorageLayout
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProvider.SingleEntryStorageLayout
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.StorageLockContext
import java.io.IOException


/**
 * Provides a default index storage implementation: [DefaultStorageLayout]/[SingleEntryStorageLayout]
 */
internal class DefaultIndexStorageLayoutProvider : FileBasedIndexLayoutProvider {

  override fun <K, V> getLayout(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorageLayout<K, V> {
    if (extension is SingleEntryFileBasedIndexExtension<V>) {
      @Suppress("UNCHECKED_CAST")
      return SingleEntryStorageLayout(extension) as VfsAwareIndexStorageLayout<K, V>
    }
    return DefaultStorageLayout(extension)
  }

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean = true

  override fun isSupported(): Boolean = true

  internal class DefaultStorageLayout<K, V>(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
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
      LOG.info("Clearing storage data for: $extension")
      deleteIndexDirectory(extension)
    }
  }

  internal class SingleEntryStorageLayout<V> internal constructor(private val extension: SingleEntryFileBasedIndexExtension<V>) : VfsAwareIndexStorageLayout<Int, V> {
    private val storageLockContext = newStorageLockContext()

    @Throws(IOException::class)
    override fun openIndexStorage(): IndexStorage<Int, V> {
      return createIndexStorage(extension, storageLockContext)
    }

    override fun openForwardIndex(): ForwardIndex {
      return EmptyForwardIndex()
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<Int, V> {
      return SingleEntryIndexForwardIndexAccessor(extension)
    }

    override fun clearIndexData() {
      LOG.info("Clearing storage data for: $extension")
      deleteIndexDirectory(extension)
    }
  }
}

private val LOG = logger<DefaultIndexStorageLayoutProvider>()

private fun deleteIndexDirectory(extension: FileBasedIndexExtension<*, *>) {
  FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(extension.name).toFile())
}

private fun newStorageLockContext(): StorageLockContext {
  return StorageLockContext(false, true)
}

@Throws(IOException::class)
private fun <K, V> createIndexStorage(extension: FileBasedIndexExtension<K, V>, storageLockContext: StorageLockContext): VfsAwareIndexStorage<K, V> {
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

//@ApiStatus.Internal
//private fun <Key, Value> getForwardIndexAccessor(indexExtension: IndexExtension<Key, Value, *>): AbstractMapForwardIndexAccessor<Key, Value, *> {
//  return if (indexExtension !is SingleEntryFileBasedIndexExtension<*> || FileBasedIndex.USE_IN_MEMORY_INDEX) {
//    MapForwardIndexAccessor(InputMapExternalizer(indexExtension))
//  }
//  else SingleEntryIndexForwardIndexAccessor(indexExtension as IndexExtension<Int, Any, *>) as AbstractMapForwardIndexAccessor<Key, Value, *>
//}

