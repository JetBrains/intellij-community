// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

fun <K, V> hashSetMultiMap(): Multimap<K, V> = Multimap { HashSet() }
fun <K, V> arrayListMultiMap(): Multimap<K, V> = Multimap { ArrayList() }
fun longLongArrayListMultiMap(): Multimap<Long, Long> = Multimap { ArrayList() } // TODO: implement LongArrayList

class Multimap<K, V>(private val factory: () -> MutableCollection<V> = linkedHashSet()) : Iterable<Map.Entry<K, List<V>>> {
  private val map = LinkedHashMap<K, MutableCollection<V>>()

  class Entry<K, V>(override val key: K, override val value: V) : Map.Entry<K, V>

  override fun iterator(): Iterator<Map.Entry<K, List<V>>> = entries().iterator()

  fun putAll(key: K, value: Iterable<V>) {
    val list = map.getOrPut(key, factory)
    list.addAll(value)
  }

  fun putAll(key: K, value: Array<out V>) {
    val list = map.getOrPut(key, factory)
    list.addAll(value)
  }

  fun put(key: K, value: V) {
    val list = map.getOrPut(key, factory)
    list.add(value)
  }

  fun remove(key: K): Collection<V>? =
    map.remove(key)

  fun removeValue(key: K, value: V) {
    val values = map[key]
    if (values != null) {
      values.remove(value)
      if (values.isEmpty())
        map.remove(key)
    } else {
      error("Can not remove value at key: $key")
    }
  }

  fun clear() {
    map.clear()
  }

  fun containsKey(key: K): Boolean = map.containsKey(key)
  fun entries(): List<Map.Entry<K, List<V>>> = map.entries.map { entry -> Entry(entry.key, entry.value.toList()) }
  operator fun get(key: K): Collection<V> = map[key] ?: emptySet()
  fun isEmpty(): Boolean = map.isEmpty()
  fun keys(): Set<K> = map.keys
  fun size(): Int = map.size
  fun values(): Collection<Collection<V>> = map.values
  fun elements(): Collection<Pair<K, V>> = entries().map { entry -> entry.value.map { entry.key to it } }.flatten()
}

private val linkedHashSetFactory = { LinkedHashSet<Any?>() }

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
private inline fun <V> linkedHashSet(): () -> MutableSet<V> = linkedHashSetFactory as () -> MutableSet<V>