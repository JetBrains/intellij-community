// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.SystemProperties
import com.intellij.util.containers.hash.EqualityPolicy
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.concurrent.withLock

//Caffeine-based [MapIndexStorageCacheProvider] implementations.
//Moved here from 'platform.util' there other providers implementations live to detach 'platform.util' from Caffeine lib dependency

/** Implementation uses [com.github.benmanes.caffeine.cache.Caffeine] cache under the hood */
@Suppress("unused")
@ApiStatus.Internal
class CaffeineIndexStorageCacheProvider : MapIndexStorageCacheProvider {
  /** Offload ValueContainer persistence to Dispatchers.IO */
  private val OFFLOAD_IO = SystemProperties.getBooleanProperty("indexes.storage-cache.offload-io", false)

  init {
    thisLogger().info("Caffeine cache will be used for indexes (offload IO: $OFFLOAD_IO)")
  }

  override fun <Key : Any, Value> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
    ): MapIndexStorageCache<Key, Value> = CaffeineCache(valueReader, evictedValuesPersister, OFFLOAD_IO, hashingStrategy, cacheSizeHint)

  //TODO RC: implement
  override fun totalReads(): Long = 0
  override fun totalReadsUncached(): Long = 0
  override fun totalEvicted(): Long = 0

  private class CaffeineCache<Key : Any, Value>(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    offloadIO: Boolean,
    private val equalityPolicy: EqualityPolicy<Key>,
    cacheSize: Int,
  ) : MapIndexStorageCache<Key, Value> {

    private val cache: LoadingCache<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>>

    init {

      val valuesLoader = CacheLoader<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>> { wrappedKey ->
        valueReader.apply(wrappedKey.key)
      }

      val onEvict = RemovalListener<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>> { wrappedKey, container, _ ->
        //key/value could be null only for weak keys/values, if an apt object is already collected.
        // It is not our configuration, but let's guard anyway:
        if (container != null && wrappedKey != null) {
          evictedValuesPersister.accept(wrappedKey.key, container)
        }
      }

      val evictionExecutor = if (offloadIO)
        Dispatchers.IO.asExecutor()
      else
        Executor { it.run() } //SameThreadExecutor is not available in this module

      //TODO RC: use maximumWeight() in terms of ValueContainer size (!= .size(), but actual memory content at the moment)
      //TODO RC: for keys there equalityPolicy is the same as Key.equals()/hashCode() we could skip creating KeyWithCustomEquality
      //         wrapper
      cache = Caffeine.newBuilder()
        .maximumSize(cacheSize.toLong())
        .executor(evictionExecutor)
        .removalListener(onEvict)
        .build(valuesLoader)
    }

    override fun read(key: Key): ChangeTrackingValueContainer<Value> = cache.get(KeyWithCustomEquality(key, equalityPolicy))

    override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? = cache.getIfPresent(KeyWithCustomEquality(key, equalityPolicy))

    override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = cache.asMap().values

    override fun invalidateAll() = cache.invalidateAll()

    /**
     * Caffeine doesn't allow customizing equals/hashCode evaluation strategy, hence we need to create a wrapper around
     * the actual Key, and customize equals/hashCode via equalityPolicy in the wrapper.
     */
    private class KeyWithCustomEquality<K>(val key: K, private val equality: EqualityPolicy<K>) {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        @Suppress("UNCHECKED_CAST")
        other as KeyWithCustomEquality<K>

        if (equality !== other.equality) {
          return false
        }
        return equality.isEqual(key, other.key)
      }

      override fun hashCode(): Int = equality.getHashCode(key)
    }
  }
}

/**
 * Implementation uses single (per-provider) [Caffeine] cache, shared by all individual caches created by the same
 * provider.
 * Each individual cache is assigned an `subCacheId`, and its entries in the shared cache are keyed by
 * `SharedCacheKey(originalKey, subCacheId, ...)`
 */
@Suppress("unused")
@ApiStatus.Internal
class SharedCaffeineIndexStorageCacheProvider(totalCacheSize: Long = (256 * 1024)) : MapIndexStorageCacheProvider {

  init {
    thisLogger().info("Caffeine shared cache will be used for indexes (totalCacheSize: $totalCacheSize)")
  }

  //TODO RC: use maximumWeight() in terms of ValueContainer size (!= .size(), but actual memory content at the moment)
  private val sharedCache: LoadingCache<SharedCacheKey<Any>, ChangeTrackingValueContainer<Any?>> = Caffeine.newBuilder()
    .maximumSize(totalCacheSize)
    .executor(Executor { it.run() })
    .removalListener(RemovalListener<SharedCacheKey<Any>, ChangeTrackingValueContainer<Any?>> { sharedCacheKey, container, _ ->
      //key/value could be null only for weak keys/values, if an apt object is already collected.
      // It is not our configuration, but let's guard anyway:
      if (container != null && sharedCacheKey != null) {
        val cacheInfo = subCachesDescriptors.get()[sharedCacheKey.subCacheId]
        check(cacheInfo != null) { "Cache registration info for ${sharedCacheKey} not found" }
        cacheInfo.cacheAccessLock.withLock {
          cacheInfo.evictedValuesPersister.accept(sharedCacheKey.key, container)
        }
      }
    })
    .build(CacheLoader { sharedCacheKey ->
      val cacheInfo = subCachesDescriptors.get()[sharedCacheKey.subCacheId]
      check(cacheInfo != null) { "Cache registration info for ${sharedCacheKey} not found" }
      val valueReader = cacheInfo.valueReader
      val key = sharedCacheKey.key
      valueReader.apply(key)
    })

  /** Map[subCacheId -> SubCacheDescriptor], updated with CopyOnWrite */
  private val subCachesDescriptors: AtomicReference<Int2ObjectMap<SubCacheDescriptor<in Any, in Any?>>> = AtomicReference(
    Int2ObjectOpenHashMap())


  override fun <Key : Any, Value : Any?> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
  ): MapIndexStorageCache<Key, Value> {
    val newDescriptor = SubCacheDescriptor(valueReader, evictedValuesPersister)
    while (true) { //CAS loop:
      val currentDescriptors = subCachesDescriptors.get()
      val updatedDescriptors = Int2ObjectOpenHashMap(currentDescriptors)
      val subCacheId = updatedDescriptors.size + 1

      check(!updatedDescriptors.containsKey(subCacheId)) { "Cache with id=$subCacheId already registered" }
      run { //wrapped in .run() to avoid overly-smart-cast:
        @Suppress("UNCHECKED_CAST") //TODO RC: sort out generics here:
        updatedDescriptors[subCacheId] = newDescriptor as SubCacheDescriptor<Any, Any?>
      }
      if (subCachesDescriptors.compareAndSet(currentDescriptors, updatedDescriptors)) {
        val caffeineSharedCache = CaffeineSharedCache(subCacheId, newDescriptor, sharedCache, hashingStrategy)
        return caffeineSharedCache
      }
    }
  }

  override fun totalReads(): Long = sharedCache.stats().requestCount()

  override fun totalReadsUncached(): Long = sharedCache.stats().loadCount()

  override fun totalEvicted(): Long = sharedCache.stats().evictionCount()

  /**
   * 1. Caffeine doesn't allow customizing equals/hashCode evaluation strategy, hence the only way to customize
   *    equals/hashCode is creating a wrapper around the actual Key, and use [equalityPolicy] in the wrapper's
   *    equals/hashCode.
   * 2. Since cache is shared, we need to differentiate keys-values from sub-caches -- this is [subCacheId]
   *    is for
   */
  private class SharedCacheKey<out K>(
    val key: K,
    val subCacheId: Int,
    private val equalityPolicy: EqualityPolicy<K>,
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass !== other?.javaClass) return false

      @Suppress("UNCHECKED_CAST")
      other as SharedCacheKey<K>

      if (subCacheId != other.subCacheId) {
        return false
      }
      return equalityPolicy.isEqual(key, other.key)
    }

    override fun hashCode(): Int = equalityPolicy.getHashCode(key) * 31 + subCacheId
  }

  //@JvmRecord
  private data class SubCacheDescriptor<Key, Value>(
    val valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    val evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    val cacheAccessLock: ReentrantLock = ReentrantLock(),
  )

  private class CaffeineSharedCache<Key : Any, Value>(
    private val cacheId: Int,
    private val subCacheDescriptor: SubCacheDescriptor<Key, Value>,
    private val sharedCache: LoadingCache<SharedCacheKey<Any>, ChangeTrackingValueContainer<Any?>>,
    private val equalityPolicy: EqualityPolicy<Key>,
  ) : MapIndexStorageCache<Key, Value> {

    override fun read(key: Key): ChangeTrackingValueContainer<Value> {
      @Suppress("UNCHECKED_CAST")
      return sharedCache.get(SharedCacheKey(key, cacheId, equalityPolicy)) as ChangeTrackingValueContainer<Value>
    }

    override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? {
      @Suppress("UNCHECKED_CAST")
      return sharedCache.getIfPresent(SharedCacheKey(key, cacheId, equalityPolicy)) as ChangeTrackingValueContainer<Value>?
    }

    override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> {
      return sharedCache.asMap().entries
        .filter { it.key.subCacheId == cacheId }
        .map {
          @Suppress("UNCHECKED_CAST")
          it.value as ChangeTrackingValueContainer<Value>
        }
    }

    override fun invalidateAll() {
      sharedCache.invalidateAll(
        sharedCache.asMap().keys.asSequence()
          .filter { it.subCacheId == cacheId }
          .toList()
      )
    }
  }
}