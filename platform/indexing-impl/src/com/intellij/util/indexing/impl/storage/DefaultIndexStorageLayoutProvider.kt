// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.InputIndexDataExternalizer
import com.intellij.util.indexing.impl.forward.*
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.StorageLockContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException

private val LOG = logger<DefaultIndexStorageLayoutProvider>()

/**
 * Provides a default index storage implementation: [DefaultStorageLayout]/[SingleEntryStorageLayout]
 */
@Internal
@VisibleForTesting
class DefaultIndexStorageLayoutProvider : FileBasedIndexLayoutProvider {

  override fun <K, V> getLayout(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorageLayout<K, V> {
    if (extension is SingleEntryFileBasedIndexExtension<V>) {
      @Suppress("UNCHECKED_CAST")
      return SingleEntryStorageLayout(extension) as VfsAwareIndexStorageLayout<K, V>
    }
    return DefaultStorageLayout(extension)
  }

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean = true

  override fun isSupported(): Boolean = true

  override fun toString(): String = DefaultIndexStorageLayoutProvider::class.java.simpleName

  internal class DefaultStorageLayout<K, V>(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    private val storageLockContext = newStorageLockContext()

    private val forwardIndexAccessor = MapForwardIndexAccessor(defaultMapExternalizerFor(extension))

    private val forwardIndexRef: StorageRef<ForwardIndex, IOException> = StorageRef(
      "ForwardIndex[${extension.name}",
      {
        val indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(extension.name)
        PersistentMapBasedForwardIndex(indexStorageFile, false, false, storageLockContext)
      },
      ForwardIndex::isClosed,
      /* failIfNotClosed: */ !VfsAwareIndexStorageLayout.WARN_IF_CLEANING_UNCLOSED_STORAGE
    )
    private val indexStorageRef: StorageRef<IndexStorage<K, V>, IOException> = StorageRef(
      "IndexStorage[${extension.name}]",
      {
        createIndexStorage(extension, storageLockContext)
      },
      IndexStorage<K, V>::isClosed,
      /* failIfNotClosed: */ !VfsAwareIndexStorageLayout.WARN_IF_CLEANING_UNCLOSED_STORAGE
    )


    @Throws(IOException::class)
    @Synchronized
    override fun openIndexStorage(): IndexStorage<K, V> {
      return indexStorageRef.reopen()
    }

    @Throws(IOException::class)
    @Synchronized
    override fun openForwardIndex(): ForwardIndex {
      return forwardIndexRef.reopen()
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return forwardIndexAccessor
    }

    @Synchronized
    override fun clearIndexData() {
      indexStorageRef.ensureClosed()
      forwardIndexRef.ensureClosed()

      LOG.info("Clearing storage data for: $extension")
      deleteIndexDirectory(extension)
    }
  }

  internal class SingleEntryStorageLayout<V> internal constructor(private val extension: SingleEntryFileBasedIndexExtension<V>) : VfsAwareIndexStorageLayout<Int, V> {
    private val storageLockContext = newStorageLockContext()

    private val forwardIndexAccessor = SingleEntryIndexForwardIndexAccessor(extension)

    private val indexStorageRef: StorageRef<IndexStorage<Int, V>, IOException> = StorageRef(
      "IndexStorage[${extension.name}]",
      {
        createIndexStorage(extension, storageLockContext)
      },
      IndexStorage<Int, V>::isClosed,
      /* failIfNotClosed: */ !VfsAwareIndexStorageLayout.WARN_IF_CLEANING_UNCLOSED_STORAGE
    )

    @Throws(IOException::class)
    @Synchronized
    override fun openIndexStorage(): IndexStorage<Int, V> {
      return indexStorageRef.reopen()
    }

    override fun openForwardIndex(): ForwardIndex {
      return EmptyForwardIndex.INSTANCE
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<Int, V> {
      return forwardIndexAccessor
    }

    @Throws(IOException::class)
    @Synchronized
    override fun clearIndexData() {
      indexStorageRef.ensureClosed()

      LOG.info("Clearing storage data for: $extension")
      deleteIndexDirectory(extension)
    }
  }

}

@Internal
fun <K, V> defaultMapExternalizerFor(extension: IndexExtension<K, V, *>): DataExternalizer<Map<K, V>> {
  if (extension is CustomInputMapIndexExtension<*, *>) {
    @Suppress("UNCHECKED_CAST")
    return (extension as CustomInputMapIndexExtension<K, V>).createInputMapExternalizer()
  }

  @Suppress("UNCHECKED_CAST")
  val keysExternalizer = if (extension is CustomInputsIndexFileBasedIndexExtension<*>) {
    (extension as CustomInputsIndexFileBasedIndexExtension<K>).createExternalizer()
  }
  else {
    InputIndexDataExternalizer<K>(extension.getKeyDescriptor(), extension.getName())
  }

  if (extension is ScalarIndexExtension<K>) {
    val inputMapExternalizer = ValueLessInputMapExternalizer<K>(keysExternalizer)
    @Suppress("UNCHECKED_CAST")
    return inputMapExternalizer as DataExternalizer<Map<K, V>>
  }
  else {
    return InputMapExternalizer(keysExternalizer, extension.valueExternalizer, false)
  }
}

private fun deleteIndexDirectory(extension: FileBasedIndexExtension<*, *>) {
  FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(extension.name).toFile())
}

private fun newStorageLockContext(): StorageLockContext {
  return StorageLockContext(false, true)
}

@Throws(IOException::class)
private fun <K, V> createIndexStorage(extension: FileBasedIndexExtension<K, V>, storageLockContext: StorageLockContext): VfsAwareIndexStorage<K, V> {
  val storageFile = IndexInfrastructure.getStorageFile(extension.name)
  return object : VfsAwareMapIndexStorage<K, V>(storageFile, extension.keyDescriptor, extension.valueExternalizer, extension.cacheSize, extension.keyIsUniqueForIndexedFile(), extension.traceKeyHashToVirtualFileMapping(), extension.enableWal()) {
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

