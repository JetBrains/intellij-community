// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UNCHECKED_CAST")

package fleet.util.openmap

import fleet.multiplatform.shims.AtomicRef
import kotlinx.collections.immutable.*

interface Key<V : Any, in Domain>

typealias OpenMap<D> = BoundedOpenMap<D, Any>

typealias MutableOpenMap<D> = MutableBoundedOpenMap<D, Any>

fun <U : Any, V : Any, D> Key<V, D>.adapt(specificThis: Key<U, D>, u: U?): V {
  require(this == specificThis)
  return u as V
}

inline fun <Domain> OpenMap(builder: MutableOpenMap<Domain>.() -> Unit = {}): OpenMap<Domain> {
  val m = MutableBoundedOpenMap.from<Domain, Any>(persistentHashMapOf())
  m.builder()
  return m.persistent()
}

inline fun <Domain> OpenMap<Domain>.update(builder: MutableOpenMap<Domain>.() -> Unit): OpenMap<Domain> {
  return mutable().apply(builder).persistent()
}

interface OpenMapView<Domain> {
  operator fun <T : Any> get(k: Key<T, Domain>): T?

  operator fun contains(key: Key<*, Domain>): Boolean {
    return get(key) != null
  }
}

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.util.test"])
fun <T : Any, Domain> OpenMapView<Domain>.assoc(k: Key<T, Domain>, v: T): OpenMapView<Domain> {
  return when (this) {
    is OpenMapAdapter -> assoc(k, v)
    else -> OpenMapAdapter(this, removedKeys = persistentSetOf(), added = BoundedOpenMap.empty<Domain>().assoc(k, v))
  }
}


//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.util.test"])
fun <T : Any, Domain> OpenMapView<Domain>.dissoc(k: Key<T, Domain>): OpenMapView<Domain> {
  return when (this) {
    is OpenMapAdapter -> dissoc(k)
    else -> OpenMapAdapter(this, removedKeys = persistentSetOf(k), added = BoundedOpenMap.empty())
  }
}

private data class OpenMapAdapter<Domain>(val original: OpenMapView<Domain>,
                                          val removedKeys: PersistentSet<Key<*, Domain>>,
                                          val added: OpenMap<Domain>) : OpenMapView<Domain> {

  override fun <T : Any> get(k: Key<T, Domain>): T? {
    return added[k] ?: original[k]?.takeIf { !removedKeys.contains(k) }
  }

  fun <T : Any> assoc(k: Key<T, Domain>, v: T): OpenMapAdapter<Domain> {
    return copy(added = added.assoc(k, v))
  }

  fun <T : Any> dissoc(k: Key<T, Domain>): OpenMapAdapter<Domain> {
    return if (k in added) copy(added = added.dissoc(k)) else copy(removedKeys = removedKeys.add(k))
  }
}

interface BoundedOpenMap<Domain, V : Any> : OpenMapView<Domain> {
  companion object {
    private val Empty = PersistentBoundedOpenMap<Any, Any>(persistentHashMapOf())

    fun <Domain, V : Any> emptyBounded(): BoundedOpenMap<Domain, V> = Empty as BoundedOpenMap<Domain, V>

    fun <Domain> empty(): OpenMap<Domain> = Empty as OpenMap<Domain>

    fun <Domain, V : Any> from(m: PersistentMap<Key<out V, Domain>, V>): BoundedOpenMap<Domain, V> {
      return PersistentBoundedOpenMap(m)
    }

    fun <Domain, V : Any> from(m: Map<Key<out V, Domain>, V>): BoundedOpenMap<Domain, V> {
      return PersistentBoundedOpenMap(m.toPersistentHashMap())
    }
  }

  fun <T : V> assoc(k: Key<T, Domain>, v: T): BoundedOpenMap<Domain, V>
  fun dissoc(k: Key<out V, Domain>): BoundedOpenMap<Domain, V>

  fun isEmpty(): Boolean

  fun mutable(): MutableBoundedOpenMap<Domain, V>

  fun asMap(): PersistentMap<Key<out V, Domain>, V>
}


/**
 * OpenMap is the unification of all our map-like types with heterogeneous keys,
 * like ActionContexts, noria bindings, Composite etc.
 */
interface MutableBoundedOpenMap<Domain, V : Any> : BoundedOpenMap<Domain, V> {
  companion object {
    fun <D, V : Any> emptyBounded(): MutableBoundedOpenMap<D, V> {
      return from(persistentHashMapOf())
    }

    fun <D> empty(): MutableOpenMap<D> {
      return emptyBounded()
    }

    fun <T, V : Any> from(map: PersistentMap<Key<out V, T>, V>): MutableBoundedOpenMap<T, V> {
      return MutableBoundedOpenMapImpl(AtomicRef(map))
    }
  }

  operator fun <T : V> set(k: Key<T, Domain>, v: T)
  fun remove(k: Key<out V, Domain>)
  fun <T : V> update(key: Key<T, Domain>, f: (T?) -> T): T
  fun <T : V> getOrInit(key: Key<T, Domain>, init: () -> T): T
  fun persistent(): BoundedOpenMap<Domain, V>
}

fun <D, V : Any> BoundedOpenMap<D, V>.merge(other: BoundedOpenMap<D, V>): BoundedOpenMap<D, V> =
  when (other) {
    BoundedOpenMap.emptyBounded<D, V>() -> this
    else -> PersistentBoundedOpenMap(asMap().builder().apply {
      for ((k, v) in other.asMap()) {
        put(k, v)
      }
    }.build())
  }

operator fun <Domain, V : Any> BoundedOpenMap<Domain, V>.plus(other: BoundedOpenMap<Domain, V>): BoundedOpenMap<Domain, V> = merge(other)

infix fun <T : Any, Domain> Key<T, Domain>.assoc(value: T): OpenMap<Domain> =
  OpenMap.empty<Domain>().assoc(this, value)