// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.util.indexing.*
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.*
import com.intellij.util.indexing.snapshot.SnapshotInputMappings
import com.intellij.util.io.IOUtil
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object DefaultIndexStorageLayout {
  val forcedLayout: String? = System.getProperty("idea.index.storage.forced.layout")

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
      VfsAwareMapIndexStorage(
        storageFile,
        extension.keyDescriptor,
        extension.valueExternalizer,
        extension.cacheSize,
        extension.keyIsUniqueForIndexedFile(),
        extension.traceKeyHashToVirtualFileMapping()
      )
    }
    catch (e: IOException) {
      IOUtil.deleteAllFilesStartingWith(storageFile)
      throw e
    }
  }

  class DefaultStorageLayout<K, V>(private val myExtension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    @Throws(IOException::class)
    override fun createOrClearIndexStorage(): IndexStorage<K, V> {
      return createOrClearIndexStorage(myExtension)
    }

    @Throws(IOException::class)
    override fun createOrClearForwardIndex(): ForwardIndex {
      val indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(myExtension.name)
      return try {
        PersistentMapBasedForwardIndex(indexStorageFile.toPath(), false, false)
      }
      catch (e: IOException) {
        IOUtil.deleteAllFilesStartingWith(indexStorageFile)
        throw e
      }
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return MapForwardIndexAccessor(InputMapExternalizer(myExtension))
    }
  }

  class SnapshotMappingsStorageLayout<K, V> internal constructor(private val myExtension: FileBasedIndexExtension<K, V>,
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
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getPersistentIndexRootDir(myExtension.name))
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getIndexRootDir(myExtension.name))
    }

    @Throws(IOException::class)
    override fun createOrClearSnapshotInputMappings(): SnapshotInputMappings<K, V> {
      return initSnapshotInputMappings(myExtension)
    }

    @Throws(IOException::class)
    override fun createOrClearIndexStorage(): IndexStorage<K, V> {
      initSnapshotInputMappings(myExtension)
      return createOrClearIndexStorage(myExtension)
    }

    @Throws(IOException::class)
    override fun createOrClearForwardIndex(): ForwardIndex {
      initSnapshotInputMappings(myExtension)
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
      initSnapshotInputMappings(myExtension)
      return mySnapshotInputMappings!!.forwardIndexAccessor
    }

    init {
      if (!contentHashEnumeratorReopen) {
        deleteIndexData()
      }
    }
  }

  class SingleEntryStorageLayout<K, V> internal constructor(private val myExtension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
    override fun createOrClearSnapshotInputMappings(): SnapshotInputMappings<K, V>? {
      return null
    }

    @Throws(IOException::class)
    override fun createOrClearIndexStorage(): IndexStorage<K, V> {
      return createOrClearIndexStorage(myExtension)
    }

    override fun createOrClearForwardIndex(): ForwardIndex {
      return EmptyForwardIndex()
    }

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<K, V> {
      return SingleEntryIndexForwardIndexAccessor(myExtension as IndexExtension<Int, V, *>) as ForwardIndexAccessor<K, V>
    }
  }

}