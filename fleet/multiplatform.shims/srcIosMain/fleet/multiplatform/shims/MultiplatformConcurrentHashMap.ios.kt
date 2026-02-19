// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims


import fleet.util.multiplatform.Actual
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

@Actual
fun <K, V> MultiplatformConcurrentHashMapNative(): MultiplatformConcurrentHashMap<K, V> = MultiplatformConcurrentHashMapNativeImpl()

internal class MultiplatformConcurrentHashMapNativeImpl<K, V> : MultiplatformConcurrentHashMap<K, V> { // TODO proper multiplatform concurrent hash map
  private val map = HashMap<K, V>()
  private val lock = ReentrantLock()

  override fun put(key: K, value: V): V? = lock.withLock {
    map.put(key, value)
  }

  override fun get(key: K): V? = lock.withLock {
    map[key]
  }

  override fun remove(key: K): V? = lock.withLock {
    map.remove(key)
  }

  override fun remove(key: K, value: V): Boolean = lock.withLock {
    if (get(key) == value) {
      remove(key)
      true
    }
    else {
      false
    }
  }

  override fun putIfAbsent(key: K, value: V): V? = lock.withLock {
    if (!containsKey(key)) {
      put(key, value)
    }
    else {
      get(key)
    }
  }

  override fun computeIfAbsent(key: K, f: (K) -> V): V = lock.withLock {
    get(key) ?: f(key).also { put(key, it) }
  }

  override fun computeIfPresent(key: K, f: (K, V) -> V): V? = lock.withLock {
    get(key)?.let { oldValue ->
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

  override fun compute(key: K, f: (K, V?) -> V?): V? = lock.withLock {
    val oldValue = get(key)
    val newValue = f(key, oldValue)
    if (newValue != null) {
      put(key, newValue)
    }
    else {
      remove(key)
    }
    newValue
  }

  override val size: Int
    get() = lock.withLock {
      map.size
    }

  override fun clear() {
    lock.withLock {
      map.clear()
    }
  }

  override fun isEmpty(): Boolean = lock.withLock {
    map.isEmpty()
  }

  override fun putAll(from: Map<out K, V>) {
    lock.withLock {
      map.putAll(from)
    }
  }

  override fun containsValue(value: V): Boolean = lock.withLock {
    map.containsValue(value)
  }

  override fun containsKey(key: K): Boolean = lock.withLock {
    map.containsKey(key)
  }

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = lock.withLock {
      // Return a snapshot of entries detached from the backing map to avoid concurrent modification
      map.entries.map { (k, v) ->
        object : MutableMap.MutableEntry<K, V> {
          override val key: K = k
          override val value: V = v
          override fun setValue(newValue: V): V {
            throw UnsupportedOperationException("`setValue` is currently not supported on ConcurrentHashMap entries on iOS")
          }
        }
      }.toMutableSet()
    }

  override val keys: MutableSet<K>
    get() = lock.withLock { map.keys.toMutableSet() }

  override val values: MutableCollection<V>
    get() = lock.withLock { map.values.toMutableList() }
}
