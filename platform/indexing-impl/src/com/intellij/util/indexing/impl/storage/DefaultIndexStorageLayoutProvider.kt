// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ThrowableNotNullFunction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.CustomInputMapIndexExtension
import com.intellij.util.indexing.CustomInputsIndexFileBasedIndexExtension
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.IndexExtension
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.InputMapExternalizer
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.ValueLessInputMapExternalizer
import com.intellij.util.indexing.VfsAwareIndexStorage
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.InputIndexDataExternalizer
import com.intellij.util.indexing.impl.forward.EmptyForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex
import com.intellij.util.indexing.impl.forward.SingleEntryIndexForwardIndexAccessor
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension
import com.intellij.util.indexing.storage.sharding.ShardedStorageLayout
import com.intellij.util.io.ChannelsAccessor.FileChannelOpener
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.OpenChannelsCache
import com.intellij.util.io.PageCacheUtils
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.writeaheadlog.ByteArrayQueueWriteAheadLog
import com.intellij.util.io.writeaheadlog.FileChannelWithWAL
import com.intellij.util.io.writeaheadlog.WriteAheadLog
import com.intellij.util.io.writeaheadlog.WriteAheadLog.ToFileWriter
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path

private val LOG = logger<DefaultIndexStorageLayoutProvider>()

/**
 * Provides a default index storage implementation: [DefaultStorageLayout]/[SingleEntryStorageLayout]
 */
@Internal
@VisibleForTesting
class DefaultIndexStorageLayoutProvider(
  private val storageLockContextFactory: () -> StorageLockContext,
) : FileBasedIndexLayoutProvider {

  constructor() : this(::newStorageLockContext)

  override fun <K, V> getLayout(
    extension: FileBasedIndexExtension<K, V>,
    otherApplicableProviders: Iterable<FileBasedIndexLayoutProvider>,
  ): VfsAwareIndexStorageLayout<K, V> {
    if (extension is SingleEntryFileBasedIndexExtension<V>) {
      @Suppress("UNCHECKED_CAST")
      return SingleEntryStorageLayout(extension, storageLockContextFactory) as VfsAwareIndexStorageLayout<K, V>
    }
    else if (extension is ShardableIndexExtension && extension.shardsCount() > 1) {
      val (storageFactory, forwardFactory) = createDefaultFactories(extension, storageLockContextFactory)
      return ShardedStorageLayout(
        extension,
        forwardFactory,
        storageFactory
      )
    }
    else {
      return DefaultStorageLayout(extension, storageLockContextFactory)
    }
  }

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean = true

  override fun isSupported(): Boolean = true

  override fun toString(): String = DefaultIndexStorageLayoutProvider::class.java.simpleName

  internal class DefaultStorageLayout<K, V>(
    private val extension: FileBasedIndexExtension<K, V>,
    storageLockContextFactory: () -> StorageLockContext,
  ) : VfsAwareIndexStorageLayout<K, V> {
    private val storageLockContext = storageLockContextFactory()

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

  internal class SingleEntryStorageLayout<V> internal constructor(
    private val extension: SingleEntryFileBasedIndexExtension<V>,
    storageLockContextFactory: () -> StorageLockContext,
  ) : VfsAwareIndexStorageLayout<Int, V> {
    private val storageLockContext = storageLockContextFactory()

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

private data class StorageFactories<K, V>(
  val storageFactory: ThrowableNotNullFunction<Int, VfsAwareIndexStorage<K, V>, IOException>,
  val forwardFactory: ThrowableNotNullFunction<Int, ForwardIndex, IOException>,
)

private fun <K, V> createDefaultFactories(
  extension: FileBasedIndexExtension<K, V>,
  storageLockContextFactory: () -> StorageLockContext,
): StorageFactories<K, V> {
  val shardsCount = (extension as ShardableIndexExtension).shardsCount()

  val storageLockContexts = Array(shardsCount) { storageLockContextFactory() }

  val storageFactory = ThrowableNotNullFunction<Int, VfsAwareIndexStorage<K, V>, IOException> { shardNo ->
    val shardStorageFile = IndexInfrastructure.getStorageFile(extension.name, shardNo)
    val storageLockContext = storageLockContexts[shardNo]
    object : VfsAwareMapIndexStorage<K, V>(
      shardStorageFile,
      extension.keyDescriptor,
      extension.valueExternalizer,
      extension.cacheSize,
      extension.keyIsUniqueForIndexedFile(),
      extension.traceKeyHashToVirtualFileMapping(),
      extension.enableWal()
    ) {
      override fun initMapAndCache() {
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
  val forwardFactory = ThrowableNotNullFunction<Int, ForwardIndex, IOException> { shardNo ->
    val shardStorageFile = IndexInfrastructure.getInputIndexStorageFile(extension.name, shardNo)
    PersistentMapBasedForwardIndex(shardStorageFile, false, false, storageLockContexts[shardNo])
  }
  return StorageFactories(storageFactory, forwardFactory)
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
  FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(extension.name))
}


private val VIA_CHANNELS_CACHE_FILE_WRITER = ToFileWriter { path: Path, offsetInFile: Long, buffer: ByteBuffer ->
  PageCacheUtils.getCachedChannelsAccessor(/*readOnly = */false).executeOp(path) { channel: FileChannel ->
    var offset = offsetInFile
    while (buffer.hasRemaining()) {
      offset += channel.write(buffer, offset)
    }
  }
}

/** Valid values: `null`/`'disabled'`, `'persistent'`, `'in-memory'` (for debugging)  */
private val USE_WRITE_AHEAD_LOG = System.getProperty("indexes.use-write-ahead-log", "persistent")

//The WAL is opened but never closed -- because currently there is no clear lifecycle ownership for the WAL.
// It is hard to pinpoint the trigger for 'WAL is not needed anymore and can be closed': WAL lifespan should
// enclose either FilePageCache or Indexes lifespan (depending on how you see the WAL ownership), but both
// FilePageCache and Indexes have lifespan ~= application, and not very well-defined (could be closed by either
// regular way or by ShutDownTracker) => it is hard to define WAL lifespan too.
//Luckily, WAL _could_ live without a well-defined lifespan: the current WAL implementation over the mmapped
// file allows WAL to still work correctly even being NOT properly closed (at least as long as OS is not crashing
// and keeps mmapped pages safe) -- so we don't close WAL and rely on this property for now.
private val WRITE_AHEAD_LOG = when (USE_WRITE_AHEAD_LOG) {
  "disabled", null -> null
  "persistent" -> setupPersistentWAL(PathManager.getIndexRoot())
  "in-memory" -> setupInMemoryWAL() //for debugging
  else -> throw ExceptionInInitializerError(
    "Unrecognized value (=$USE_WRITE_AHEAD_LOG) of 'indexes.use-write-ahead-log' system property. " +
    "Use recognizable values: ('persistent', 'in-memory', 'disabled'/null)"
  )
}

private val CHANNEL_WITH_WAL_OPENER = FileChannelOpener { path: Path, readOnly: Boolean ->
  require(WRITE_AHEAD_LOG != null) { "WRITE_AHEAD_LOG is disabled" }

  val channelsAccessor = PageCacheUtils.getCachedChannelsAccessor(readOnly)
  FileChannelWithWAL(path, WRITE_AHEAD_LOG, channelsAccessor, readOnly)
}

/** Shared channels cache, with write-ahead-log feature  */
private val CHANNELS_WITH_WRITE_AHEAD_CACHE = OpenChannelsCache(
  "channels-cache-with-WAL",
  //Actually, _this_ cache's capacity is unrelated to CHANNELS_CACHE_CAPACITY -- this cache doesn't spend
  // (limited) file descriptors.
  // But the capacity should still be limited, because even FileChannelWithWAL carries some overhead.
  PageCacheUtils.CHANNELS_CACHE_CAPACITY,
  CHANNEL_WITH_WAL_OPENER
)

private fun setupInMemoryWAL(): WriteAheadLog {
  LOG.info("Opening in-memory Write-Ahead Log (not for production use!)")
  return ByteArrayQueueWriteAheadLog(VIA_CHANNELS_CACHE_FILE_WRITER)
}

private fun setupPersistentWAL(directory: Path): WriteAheadLog? {
  return PersistentWriteAheadLogFactory.setup(
    directory = directory,
    flusherThreadFactory = IntelliJVirtualThreads.ofVirtual().name("WriteAheadLogFlusher").factory(),
    toFileWriter = VIA_CHANNELS_CACHE_FILE_WRITER,
    invalidateCaches = { FileBasedIndex.getInstance().invalidateCaches() },
  )
}

@VisibleForTesting
@Internal
fun newStorageLockContext(): StorageLockContext {
  if (WRITE_AHEAD_LOG != null) {
    return StorageLockContext(
      /* useReadWriteLock: */ false,
      CHANNELS_WITH_WRITE_AHEAD_CACHE.asReadOnly(),
      CHANNELS_WITH_WRITE_AHEAD_CACHE.asWritable()
    )
  }
  else {
    return StorageLockContext(/*useRWLock:*/false, /*cacheChannels:*/true)//== use regular PageCacheUtils cached accessors
  }
}

@Throws(IOException::class)
private fun <K, V> createIndexStorage(
  extension: FileBasedIndexExtension<K, V>,
  storageLockContext: StorageLockContext,
): VfsAwareIndexStorage<K, V> {
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