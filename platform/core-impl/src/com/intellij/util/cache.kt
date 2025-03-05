// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Simple atomic cache that allows to drop and lazily atomically compute new value.
 * Atomicity is important if the [Cache] is a mutable map which is also lazily populated.
 */
@Internal
open class AtomicCache<Cache : Any>(
  val newInstance: () -> Cache
) {
  private val _cache: AtomicReference<Cache> = AtomicReference(null)

  val cache: Cache
    get() {
      val value = _cache.get()
      if (value != null) {
        return value
      }

      return _cache.updateAndGet { cur ->
        cur ?: newInstance()
      }
    }

  fun invalidate() {
    _cache.set(null)
  }

  val isInitialized: Boolean
    get() = _cache.get() != null
}

@Internal
class AtomicMapCache<K : Any, V : Any, Map : ConcurrentMap<K, V>>(newInstance: () -> Map)
  : AtomicCache<Map>(newInstance) {

  operator fun get(key: K): V? = cache[key]
  operator fun set(key: K, value: V) { cache[key] = value }
  fun getOrPut(key: K, default: () -> V): V = cache.getOrPut(key, default)
  fun computeIfAbsent(key: K, default: () -> V): V = cache.computeIfAbsent(key) { default() }
}