// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.*
import com.intellij.util.indexing.snapshot.SnapshotInputMappings
import com.intellij.util.io.IOUtil
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.StorageLockContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object DefaultIndexStorageLayout {
  private val forcedLayout: String? = System.getProperty("idea.index.storage.forced.layout")
  private val contentLessIndexLock: StorageLockContext = StorageLockContext(true)

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
  fun <K, V> createOrClearIndexStorage(extension: FileBasedIndexExtension<K, V>): VfsAwareIndexStorage<K, V> {
    val storageFile = IndexInfrastructure.getStorageFile(extension.name).toPath()
    return try {
      object : VfsAwareMapIndexStorage<K, V>(
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
    catch (e: IOException) {
      IOUtil.deleteAllFilesStartingWith(storageFile)
      throw e
    }
  }

  class DefaultStorageLayout<K, V>(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    @Throws(IOException::class)
    override fun createOrClearIndexStorage(): IndexStorage<K, V> {
      return createOrClearIndexStorage(extension)
    }

    @Throws(IOException::class)
    override fun createOrClearForwardIndex(): ForwardIndex {
      val indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(extension.name)
      return try {
        val storageLockContext = if (extension.dependsOnFileContent()) null else contentLessIndexLock
        PersistentMapBasedForwardIndex(indexStorageFile.toPath(),
                                       false,
                                       false,
                                       storageLockContext)
      }
      catch (e: IOException) {
        IOUtil.deleteAllFilesStartingWith(indexStorageFile)
        throw e
      }
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return MapForwardIndexAccessor(InputMapExternalizer(extension))
    }
  }

  class SnapshotMappingsStorageLayout<K, V> internal constructor(private val extension: FileBasedIndexExtension<K, V>,
                                                                 contentHashEnumeratorReopen: Boolean) : VfsAwareIndexStorageLayout<K, V> {
    private var mySnapshotInputMappings: SnapshotInputMappings<K, V>? = null

    @Throws(IOException::class)
    private fun initSnapshotInputMappings(extension: FileBasedIndexExtension<K, V>): SnapshotInputMappings<K, V> {
      if (mySnapshotInputMappings == null) {
        mySnapshotInputMappings = try {
          SnapshotInputMappings<K, V>(extension, getForwardIndexAccessor(extension))
        }
        catch (e: IOException) {
          deleteIndexData()
          throw e
        }
      }
      return mySnapshotInputMappings!!
    }

    private fun deleteIndexData() {
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getPersistentIndexRootDir(extension.name))
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getIndexRootDir(extension.name))
    }

    @Throws(IOException::class)
    override fun createOrClearSnapshotInputMappings(): SnapshotInputMappings<K, V> {
      return initSnapshotInputMappings(extension)
    }

    @Throws(IOException::class)
    override fun createOrClearIndexStorage(): IndexStorage<K, V> {
      initSnapshotInputMappings(extension)
      return createOrClearIndexStorage(extension)
    }

    @Throws(IOException::class)
    override fun createOrClearForwardIndex(): ForwardIndex {
      initSnapshotInputMappings(extension)
      val storageFile = mySnapshotInputMappings!!.inputIndexStorageFile
      return try {
        IntMapForwardIndex(storageFile, true)
      }
      catch (e: IOException) {
        IOUtil.deleteAllFilesStartingWith(storageFile)
        throw e
      }
    }

    @Throws(IOException::class)
    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      initSnapshotInputMappings(extension)
      return mySnapshotInputMappings!!.forwardIndexAccessor
    }

    init {
      if (!contentHashEnumeratorReopen) {
        deleteIndexData()
      }
    }
  }

  class SingleEntryStorageLayout<K, V> internal constructor(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    override fun createOrClearSnapshotInputMappings(): SnapshotInputMappings<K, V>? {
      return null
    }

    @Throws(IOException::class)
    override fun createOrClearIndexStorage(): IndexStorage<K, V> {
      return createOrClearIndexStorage(extension)
    }

    override fun createOrClearForwardIndex(): ForwardIndex {
      return EmptyForwardIndex()
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return SingleEntryIndexForwardIndexAccessor(extension as IndexExtension<Int, V, *>) as ForwardIndexAccessor<K, V>
    }
  }

}