// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Simple atomic cache that allows to drop and lazily atomically compute new value.
 * Atomicity is important because the inner map is also lazily populated.
 */
@Internal
class AtomicMapCache<K : Any, V : Any>(val newInstance: () -> ConcurrentMap<K, V>) {
  private val _cache: AtomicReference<ConcurrentMap<K, V>> = AtomicReference(null)

  fun getCacheIfInitialized(): ConcurrentMap<K, V>? {
    return _cache.get()
  }

  fun getCacheOrInitialize(): ConcurrentMap<K, V> {
    return _cache.updateAndGet { cur ->
      cur ?: newInstance()
    }
  }
  fun invalidate() {
    _cache.set(null)
  }



  operator fun get(key: K): V? = getCacheIfInitialized()?.get(key)
  operator fun set(key: K, value: V) { getCacheOrInitialize()[key] = value }
  fun getOrPut(key: K, default: () -> V): V = getCacheOrInitialize().getOrPut(key, default)
  fun computeIfAbsent(key: K, default: () -> V): V = getCacheOrInitialize().computeIfAbsent(key) { default() }
}