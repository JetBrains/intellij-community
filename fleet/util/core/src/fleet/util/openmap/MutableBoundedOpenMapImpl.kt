// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.openmap

import fleet.multiplatform.shims.AtomicRef
import kotlinx.collections.immutable.PersistentMap

internal class MutableBoundedOpenMapImpl<Domain, V : Any>(val map: AtomicRef<PersistentMap<Key<out V, in Domain>, V>>) : MutableBoundedOpenMap<Domain, V> {
  override fun <T : Any> get(k: Key<T, in Domain>): T? {
    return map.get().get(k as Key<out V, Domain>) as T?
  }

  override fun isEmpty() = map.get().size == 0

  override fun <T : V> set(k: Key<T, Domain>, v: T) {
    map.updateAndGet { map -> map.put(k, v) }
  }

  override fun remove(k: Key<out V, Domain>) {
    map.updateAndGet { map -> map.remove(k) }
  }

  override fun <T : V> assoc(k: Key<T, Domain>, v: T): BoundedOpenMap<Domain, V> {
    return persistent().assoc(k, v)
  }

  override fun mutable(): MutableBoundedOpenMap<Domain, V> {
    return this
  }

  override fun persistent(): BoundedOpenMap<Domain, V> {
    return BoundedOpenMap.from(map.get())
  }

  override fun <T : V> update(key: Key<T, Domain>, f: (T?) -> T): T {
    return map.updateAndGet { map ->
      val v1 = f(map.get(key) as T?)
      map.put(key, v1)
    }.get(key) as T
  }

  override fun <T : V> getOrInit(key: Key<T, Domain>, init: () -> T): T {
    return map.updateAndGet { map ->
      val v = map.get(key)
      if (v == null) {
        map.put(key, init())
      }
      else {
        map
      }
    }.get(key) as T
  }

  override fun equals(other: Any?): Boolean {
    return other is BoundedOpenMap<*, *> && other.asMap() == asMap()
  }

  override fun hashCode(): Int {
    return asMap().hashCode()
  }

  override fun toString(): String {
    return "MutableOpenMap(${map.get()})"
  }

  override fun asMap(): PersistentMap<Key<out V, in Domain>, V> {
    return map.get()
  }

  override fun dissoc(k: Key<out V, Domain>): BoundedOpenMap<Domain, V> {
    return persistent().dissoc(k)
  }
}