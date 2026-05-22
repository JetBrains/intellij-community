// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.openmap

import kotlinx.collections.immutable.PersistentMap

data class PersistentBoundedOpenMap<Domain, V : Any>(val map: PersistentMap<Key<out V, Domain>, V>) : BoundedOpenMap<Domain, V> {

  override fun <T : V> assoc(k: Key<T, Domain>, v: T): BoundedOpenMap<Domain, V> {
    return BoundedOpenMap.from(map.put(k, v))
  }

  override fun <T : Any> get(k: Key<T, Domain>): T? {
    return map[k as Key<out V, Domain>] as T?
  }

  override fun isEmpty() = map.size == 0

  override fun mutable(): MutableBoundedOpenMap<Domain, V> {
    return MutableBoundedOpenMap.from(map)
  }

  override fun asMap(): PersistentMap<Key<out V, Domain>, V> {
    return map
  }

  override fun equals(other: Any?): Boolean {
    return other is BoundedOpenMap<*, *> && other.asMap() == asMap()
  }

  override fun toString(): String {
    return "PersistentOpenMap(${map})"
  }

  override fun hashCode(): Int {
    return map.hashCode()
  }

  override fun dissoc(k: Key<out V, Domain>): BoundedOpenMap<Domain, V> {
    return BoundedOpenMap.from(map.remove(k))
  }
}