// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.bifurcan.SortedMap

/**
 * Override of io.lacuna.bifurcan.SortedSet with fixed linear/forked mode
 */
class SortedSet<V>(val m: SortedMap<V, Unit?>) : Set<V> {
  fun comparator(): Comparator<V> {
    return m.comparator()
  }

  fun inclusiveFloorIndex(value: V): Long? {
    return m.inclusiveFloorIndex(value)
  }

  fun ceilIndex(value: V): Long? {
    return m.ceilIndex(value)
  }

  fun add(value: V): SortedSet<V> {
    val mPrime: SortedMap<V, Unit?> = m.put(value, null)
    return if (m === mPrime) {
      this
    }
    else {
      SortedSet(mPrime)
    }
  }

  fun remove(value: V): SortedSet<V> {
    val mPrime: SortedMap<V, Unit?> = m.remove(value)
    return if (m === mPrime) {
      this
    }
    else {
      SortedSet(mPrime)
    }
  }

  fun <U> zip(f: (V) -> U): SortedMap<V, U> {
    return m.mapValues { k: V, _: Unit? -> f(k) }
  }

  fun clear(): SortedSet<V> {
    val mPrime = m.clear()
    return when {
      m === mPrime -> this
      else -> SortedSet(mPrime)
    }
  }

  override fun contains(value: V): Boolean {
    return m.contains(value)
  }

  override fun containsAll(elements: Collection<V>): Boolean {
    return elements.all { v -> contains(v) }
  }

  override fun isEmpty(): Boolean {
    return m.isEmpty()
  }

  override fun iterator(): Iterator<V> {
    return m.keys.iterator()
  }

  fun indexOf(element: V): Long? {
    return m.indexOf(element)
  }

  override val size: Int
    get() {
      return m.size
    }

  fun nth(idx: Long): V {
    return m.nth(idx).key
  }

  fun elements(): BifurcanVector<V> {
    return BifurcanVector.from(m.keys)
  }

  fun forked(): SortedSet<V> {
    return if (isLinear) SortedSet<V>(m.forked()) else this
  }

  fun linear(): SortedSet<V> {
    return if (isLinear) this else SortedSet<V>(m.linear())
  }

  val isLinear: Boolean
    get() {
      return m.isLinear
    }
}

fun <V : Comparable<V>> SortedSet() = SortedSet<V>(SortedMap<V, Unit?>())