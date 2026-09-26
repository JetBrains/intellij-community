// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.psi.impl.source.tree.mvcc.VersionedPsiReference
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentMap

/**
 * Simple atomic cache that allows to drop and lazily atomically compute new value.
 * Atomicity is important because the inner map is also lazily populated.
 */
@Internal
class AtomicMapCache<K : Any, V : Any>(private val newInstance: () -> ConcurrentMap<K, V>) {
  private val _cache: VersionedPsiReference<ConcurrentMap<K, V>> = VersionedPsiReference()

  fun getCacheIfInitialized(): ConcurrentMap<K, V>? {
    return _cache.get()
  }

  fun getCacheOrInitialize(): ConcurrentMap<K, V> {
    return _cache.getOrPut(newInstance)
  }
  fun invalidate() {
    _cache.set(null)
  }



  operator fun get(key: K): V? = getCacheIfInitialized()?.get(key)
  operator fun set(key: K, value: V) { getCacheOrInitialize()[key] = value }
  fun getOrPut(key: K, default: () -> V): V = getCacheOrInitialize().getOrPut(key, default)
  fun computeIfAbsent(key: K, default: () -> V): V = getCacheOrInitialize().computeIfAbsent(key) { default() }
}