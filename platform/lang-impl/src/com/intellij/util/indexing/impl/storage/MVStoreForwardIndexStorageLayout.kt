// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.ForwardIndex
import java.io.IOException
import org.jetbrains.mvstore.MVStore
import java.lang.RuntimeException
import org.jetbrains.mvstore.index.MVStorePersistentMap
import com.intellij.util.io.ByteSequenceDataExternalizer
import com.intellij.util.io.EnumeratorIntegerDescriptor
import java.lang.Exception
import java.nio.file.Path
import java.util.function.Consumer

internal class MVStoreForwardIndexStorageLayout<K, V>(private val extension: FileBasedIndexExtension<K, V>) : VfsAwareIndexStorageLayout<K, V> {
  @Throws(IOException::class)
  override fun createOrClearIndexStorage(): IndexStorage<K, V> {
    return VfsAwareIndexStorageLayout.createOrClearIndexStorage(extension)
  }

  @Throws(IOException::class)
  override fun createOrClearForwardIndex(): ForwardIndex {
    return MVStoreForwardIndex()
  }

  object MVStoreHolder {
    private var store: MVStore? = null
    private val lock = Any()

    @Synchronized
    fun getStore(path: Path): MVStore {
      synchronized(lock) {
        if (store == null) {
          val initializationException = Ref.create<Exception>()
          store = MVStore.Builder().openOrNewOnIoError(path.resolve("mv.forward.indexes"), true,
                                                       Consumer { e -> initializationException.set(e) })
          if (!initializationException.isNull) {
            throw RuntimeException(initializationException.get())
          }
          ShutDownTracker.getInstance().registerShutdownTask {
            synchronized(lock) {
              store?.close()
              store = null
            }
          }
        }
        return store!!
      }
    }
  }

  private inner class MVStoreForwardIndex : ForwardIndex {
    @Volatile
    private var map = createMap()

    private fun createMap(): MVStorePersistentMap<Int, ByteArraySequence> {
      return MVStorePersistentMap(extension.name.name,
                                  MVStoreHolder.getStore(PathManager.getIndexRoot().toPath()),
                                  EnumeratorIntegerDescriptor.INSTANCE,
                                  ByteSequenceDataExternalizer.INSTANCE)
    }

    @Throws(IOException::class)
    override fun close() = map.close()

    @Throws(IOException::class)
    override fun get(key: Int): ByteArraySequence? = map[key]

    @Throws(IOException::class)
    override fun put(key: Int, value: ByteArraySequence?) {
      if (value == null) {
        map.remove(key)
      }
      else {
        map.put(key, value)
      }
    }

    override fun force() = map.force()

    @Throws(IOException::class)
    override fun clear() {
      map.closeAndDelete()
      map = createMap()
    }
  }
}