// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.bifurcan

import fleet.util.BifurcanMapSerializer
import fleet.util.bifurcan.nodes.SortedMapNodes
import kotlinx.serialization.Serializable

/**
 * A red-black tree based on [Germane 2014](http://matt.might.net/papers/germane2014deletion.pdf).
 */
@Serializable(with = BifurcanMapSerializer::class)
class SortedMap<K, V> internal constructor(
  var root: SortedMapNodes.Node<K, V>,
  linear: Boolean,
  private val comparator: Comparator<K>,
) : Map<K, V> {
  private val editor: Any? = if (linear) Any() else null

  constructor(comparator: Comparator<K>) : this(SortedMapNodes.BlackLeaf as SortedMapNodes.Node<K, V>, false, comparator)

  fun comparator(): Comparator<K> {
    return comparator
  }

  fun inclusiveFloorIndex(key: K): Long? {
    val idx = root.floorIndex(key, comparator, 0)
    return if (idx < 0) null else idx
  }

  fun ceilIndex(key: K): Long? {
    val idx = root.ceilIndex(key, comparator, 0)
    return if (idx < 0) null else idx
  }

  fun update(key: K, update: (V?) -> V): SortedMap<K, V> {
    return put(key, update(this.get(key, null)))
  }

  fun put(key: K, value: V): SortedMap<K, V> {
    return put(key, value) { _, r -> r }
  }

  fun put(key: K, value: V, merge: (V, V) -> V): SortedMap<K, V> {
    val rootPrime = root.put(key, value, merge, comparator)
    //rootPrime.checkInvariant();
    if (this.isLinear) {
      root = rootPrime
      return this
    }
    else {
      return SortedMap<K, V>(rootPrime, false, comparator)
    }
  }

  fun remove(key: K): SortedMap<K, V> {
    val rootPrime = root.remove(key, comparator)
    //rootPrime.checkInvariant();
    if (this.isLinear) {
      root = rootPrime
      return this
    }
    else {
      return SortedMap<K, V>(rootPrime, false, comparator)
    }
  }

  fun clear(): SortedMap<K, V> {
    if (isLinear) {
      root = SortedMapNodes.BlackLeaf as SortedMapNodes.Node<K, V>
      return this
    }
    else {
      return SortedMap<K, V>(SortedMapNodes.BlackLeaf as SortedMapNodes.Node<K, V>, false, comparator)
    }
  }

  override fun get(key: K): V? = get(key, null)

  fun get(key: K, defaultValue: V?): V? {
    val n = SortedMapNodes.find<K, V>(root, key, comparator)
    return if (n == null) defaultValue else n.v
  }

  override fun containsKey(key: K): Boolean {
    return SortedMapNodes.find<K, V>(root, key, comparator) != null
  }

  override fun containsValue(value: V): Boolean {
    return any { it.value == value }
  }

  fun indexOf(key: K): Long? {
    val idx = SortedMapNodes.indexOf<K, V>(root, key, comparator)
    return if (idx < 0) null else idx
  }

  fun <U> mapValues(f: (K, V) -> U): SortedMap<K, U> {
    return SortedMap<K, U>(root.mapValues<U>(f), this.isLinear, comparator)
  }

  fun iterator(): Iterator<Map.Entry<K, V>> {
    return SortedMapNodes.iterator<K, V>(root)
  }

  fun nth(idx: Long): Map.Entry<K, V> {
    if (idx < 0 || idx >= size) {
      throw IndexOutOfBoundsException("$idx must be within [0,$size)")
    }
    val n = SortedMapNodes.nth<K, V>(root, idx.toInt())
    return object : Map.Entry<K, V> {
      override val key: K = n.k
      override val value: V = n.v
    }
  }

  override val entries: Set<Map.Entry<K, V>>
    get() = iterator().asSequence().toSet()
  override val keys: Set<K>
    get() = map { it.key }.toSet()

  override val size: Int
    get() {
      return root.size.toInt()
    }

  override fun isEmpty(): Boolean {
    return size == 0
  }

  override val values: Collection<V>
    get() = map { it.value }

  fun clone(): SortedMap<K, V> {
    return if (this.isLinear) forked().linear() else this
  }

  val isLinear: Boolean
    get() = editor != null

  fun forked(): SortedMap<K, V> {
    return if (this.isLinear) SortedMap<K, V>(root, false, comparator) else this
  }

  fun linear(): SortedMap<K, V> {
    return if (this.isLinear) this else SortedMap<K, V>(root, true, comparator)
  }

  companion object {
    fun <K : Comparable<K>, V> from(m: Map<K, V>): SortedMap<K, V> {
      val result = SortedMap<K, V>().linear()
      m.entries.forEach { e -> result.put(e.key, e.value) }
      return result.forked()
    }
  }
}

fun <K : Comparable<K>, V> SortedMap(): SortedMap<K, V> =
  SortedMap<K, V>(SortedMapNodes.BlackLeaf as SortedMapNodes.Node<K, V>, false, naturalOrder<K>())
