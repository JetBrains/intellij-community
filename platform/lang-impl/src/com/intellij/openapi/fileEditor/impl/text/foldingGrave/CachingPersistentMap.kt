// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.PersistentMapBase
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

internal class CachingPersistentMap<K, V>(
  private val persistentMap: PersistentMapBase<K, V>,
  inMemoryCount: Int = 20,
) {
  private val inMemoryMap: LinkedHashMap<K, InMemoryValue<V>> = createInMemoryMap(inMemoryCount)
  private val rwLock: ReadWriteLock = ReentrantReadWriteLock()
  private val removedValue: InMemoryValue<V> = InMemoryValue(null, AtomicBoolean(false))

  companion object {
    private val logger = Logger.getInstance(CachingPersistentMap::class.java)

    /**
     * @param value The value stored in memory. Null represents removed value.
     * @param isDirty A flag indicating if the value has been modified and needs to be persisted.
     */
    private data class InMemoryValue<V>(val value: V?, val isDirty: AtomicBoolean)
  }

  @RequiresBackgroundThread
  operator fun get(key: K): V? {
    rwLock.readLock().lock()
    try {
      val inMemoryValue = inMemoryMap[key]
      if (inMemoryValue != null) {
        return inMemoryValue.value
      }
    } finally {
      rwLock.readLock().unlock()
    }
    rwLock.writeLock().lock()
    try {
      val inMemoryValue = inMemoryMap[key]
      if (inMemoryValue != null) {
        return inMemoryValue.value
      }
      val value = persistentMap[key]
      if (value != null) {
        inMemoryMap[key] = InMemoryValue(value, isDirty=AtomicBoolean(false))
        return value
      }
      return null
    } finally {
      rwLock.writeLock().unlock()
    }
  }

  @RequiresBackgroundThread
  operator fun set(key: K, value: V) {
    rwLock.writeLock().lock()
    try {
      inMemoryMap[key] = InMemoryValue(value, isDirty=AtomicBoolean(true))
    } finally {
      rwLock.writeLock().unlock()
    }
  }

  @RequiresBackgroundThread
  fun remove(key: K) {
    rwLock.writeLock().lock()
    try {
      inMemoryMap[key] = removedValue
    } finally {
      rwLock.writeLock().unlock()
    }
  }

  @RequiresBackgroundThread
  fun flush() {
    var persistedCount = 0
    rwLock.readLock().lock()
    try {
      for (entry in inMemoryMap.entries) {
        try {
          val persisted = persistIfDirty(entry)
          if (persisted) {
            persistedCount++
          }
        } catch (e: IOException) {
          logger.info("error while flushing persistent map ", e)
        }
      }
    } finally {
      rwLock.readLock().unlock()
    }
    if (persistedCount > 0) {
      logger.debug { "flushing $persistedCount folding states" }
    }
    persistentMap.force()
  }

  @RequiresBackgroundThread
  fun close() {
    flush()
    if (logger.isDebugEnabled) {
      var size = -1
      try {
        size = persistentMap.keysCount()
      } catch (ignored: IOException) {}
      logger.debug("closing folding persistent map with size $size")
    }
    persistentMap.close()
  }

  override fun toString(): String {
    var keysCount = -1
    try {
      keysCount = persistentMap.keysCount()
    } catch (ignored: IOException) {}
    return "inMemoryCount=${inMemoryMap.size}, persistentCount=$keysCount"
  }

  private fun createInMemoryMap(inMemoryCount: Int) = object : LinkedHashMap<K, InMemoryValue<V>>() {
    override fun removeEldestEntry(eldestEntry: MutableMap.MutableEntry<K, InMemoryValue<V>>): Boolean {
      val shouldEvict = size > inMemoryCount
      if (shouldEvict) {
        persistIfDirty(eldestEntry)
      }
      return shouldEvict
    }
  }

  private fun persistIfDirty(entry: MutableMap.MutableEntry<K, InMemoryValue<V>>): Boolean {
    val (key, inMemoryValue) = entry
    if (inMemoryValue.isDirty.compareAndSet(true, false)) {
      persist(key, inMemoryValue)
      return true
    }
    return false
  }

  @RequiresBackgroundThread
  private fun persist(key: K, inMemoryValue: InMemoryValue<V>) {
    if (inMemoryValue.value != null) {
      persistentMap.put(key, inMemoryValue.value)
    } else {
      persistentMap.remove(key)
    }
  }
}
