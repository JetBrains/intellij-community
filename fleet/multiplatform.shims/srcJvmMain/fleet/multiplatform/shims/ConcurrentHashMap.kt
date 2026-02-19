// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import java.util.concurrent.ConcurrentHashMap as JavaConcurrentHashMap

/**
 * @deprecated Use [java.util.concurrent.ConcurrentHashMap]
 *
 * Factory method is not supposed to be used outside the KMP context.
 *
 * Moreover, using it may lead to quite tricky bugs while using extension methods on MutableMap,
 * that are not ready for concurrent execution, instead of the one defined on ConcurrentHashMap (e.g. `getOrPut function)
 */
@Deprecated("Use ConcurrentHashMap from java.util.concurrent", ReplaceWith("ConcurrentHashMap<K, V>()", "java.util.concurrent.ConcurrentHashMap"))
fun <K, V> ConcurrentHashMap(): ConcurrentHashMap<K, V> = ConcurrentHashMapImpl<K, V>(JavaConcurrentHashMap<K, V>())

/**
 * @deprecated Use [java.util.concurrent.ConcurrentHashMap]
 *
 * Interface is not supposed to be used outside the KMP context.
 *
 * Moreover, using it may lead to quite tricky bugs while using extension methods on MutableMap,
 * that are not ready for concurrent execution, instead of the one defined on ConcurrentHashMap (e.g. `getOrPut function)
 */
@Deprecated("Use ConcurrentHashMap from java.util.concurrent", ReplaceWith("ConcurrentHashMap<K, V>", "java.util.concurrent.ConcurrentHashMap"))
interface ConcurrentHashMap<K, V> : MutableMap<K, V> {
  fun putIfAbsent(key: K, value: V): V?

  fun computeIfAbsent(key: K, f: (K) -> V): V

  fun computeIfPresent(key: K, f: (K, V) -> V): V?

  fun compute(key: K, f: (K, V?) -> V?): V?

  fun remove(key: K, value: V): Boolean
}

private class ConcurrentHashMapImpl<K, V>(private val hashMap: JavaConcurrentHashMap<K, V>) : MutableMap<K, V> by hashMap, ConcurrentHashMap<K, V> {
  override fun remove(key: K, value: V): Boolean {
    return hashMap.remove(key, value)
  }

  override fun putIfAbsent(key: K, value: V): V? {
    return hashMap.putIfAbsent(key, value)
  }

  override fun computeIfAbsent(key: K, f: (K) -> V): V {
    return hashMap.computeIfAbsent(key, f)
  }

  override fun computeIfPresent(key: K, f: (K, V) -> V): V? {
    return hashMap.computeIfPresent(key, f)
  }

  override fun compute(key: K, f: (K, V?) -> V?): V? {
    return hashMap.compute(key, f)
  }
}