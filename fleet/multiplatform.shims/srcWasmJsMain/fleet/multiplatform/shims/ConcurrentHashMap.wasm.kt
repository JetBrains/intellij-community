// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims


import fleet.util.multiplatform.Actual

@Actual("ConcurrentHashMap")
fun <K, V> ConcurrentHashMapWasmJs(): ConcurrentHashMap<K, V> = ConcurrentHashMapWasm(mutableMapOf())

internal fun <K, V> ConcurrentHashMapWasm(base: MutableMap<K, V>): ConcurrentHashMap<K, V> = object : MutableMap<K, V> by base, ConcurrentHashMap<K, V> {
  override fun putIfAbsent(key: K, value: V): V? {
    return if (!containsKey(key)) {
      put(key, value)
    }
    else {
      get(key)
    }
  }

  override fun computeIfAbsent(key: K, f: (K) -> V): V {
    return get(key) ?: f(key).also { put(key, it) }
  }

  override fun computeIfPresent(key: K, f: (K, V) -> V): V? {
    return get(key)?.let { oldValue ->
      val newValue = f(key, oldValue)
      if (newValue != null) {
        put(key, newValue)
      }
      else {
        remove(key)
      }
      newValue
    }
  }

  override fun compute(key: K, f: (K, V?) -> V?): V? {
    val oldValue = get(key)
    val newValue = f(key, oldValue)
    if (newValue != null) {
      put(key, newValue)
    }
    else {
      remove(key)
    }
    return newValue
  }

  override fun remove(key: K, value: V): Boolean {
    return if (get(key) == value) {
      remove(key)
      true
    }
    else {
      false
    }
  }

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = base.entries.toMutableSet()

  override val keys: MutableSet<K>
    get() = base.keys.toMutableSet()

  override val values: MutableCollection<V>
    get() = base.values.toMutableList()
}