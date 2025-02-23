// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims


import fleet.util.multiplatform.Actual
import java.util.concurrent.ConcurrentHashMap as JavaConcurrentHashMap


@Actual(linkedTo = "ConcurrentHashMap")
internal fun <K, V> ConcurrentHashMapJvm(): ConcurrentHashMap<K, V> =
  object : ConcurrentHashMap<K, V> {
  val hashMap = JavaConcurrentHashMap<K, V>()

  override fun remove(key: K): V? = hashMap.remove(key)

  override fun putIfAbsent(key: K, value: V): V? = hashMap.putIfAbsent(key, value)

  override fun computeIfAbsent(key: K, f: (K) -> V): V = hashMap.computeIfAbsent(key, f)

  override fun compute(key: K, f: (K, V?) -> V?): V? = hashMap.compute(key, f)

  override fun remove(key: K, value: V): Boolean = hashMap.remove(key, value)

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = hashMap.entries
  override val keys: MutableSet<K>
    get() = hashMap.keys
  override val size: Int
    get() = hashMap.size
  override val values: MutableCollection<V>
    get() = hashMap.values

  override fun clear() {
    hashMap.clear()
  }

  override fun isEmpty(): Boolean = hashMap.isEmpty()

  override fun putAll(from: Map<out K, V>) {
    hashMap.putAll(from)
  }

  override fun put(key: K, value: V): V? {
    return if (value == null) {
      hashMap.remove(key)
    } else {
      hashMap.put(key!!, value)
    }
  }

  override fun get(key: K): V? = hashMap[key]

  override fun containsValue(value: V): Boolean = hashMap.containsValue(value)

  override fun containsKey(key: K): Boolean = hashMap.containsKey(key)
}