// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import io.lacuna.bifurcan.*
import io.lacuna.bifurcan.SortedMap
import java.util.OptionalLong
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.ToLongFunction

/**
 * Override of io.lacuna.bifurcan.SortedSet with fixed linear/forked mode
 */
class SortedSet<V>(val m: SortedMap<V, Void?> = SortedMap<V, Void?>()) : ISortedSet.Mixin<V>() {
  override fun comparator(): Comparator<V> {
    return m.comparator()
  }

  override fun inclusiveFloorIndex(value: V): OptionalLong {
    return m.inclusiveFloorIndex(value)
  }

  override fun ceilIndex(value: V): OptionalLong? {
    return m.ceilIndex(value)
  }

  override fun add(value: V): SortedSet<V> {
    val mPrime: SortedMap<V, Void?> = m.put(value, null)
    return if (m === mPrime) {
      super.hash = -1
      this
    }
    else {
      SortedSet(mPrime)
    }
  }

  override fun remove(value: V): SortedSet<V> {
    val mPrime: SortedMap<V, Void?> = m.remove(value)
    return if (m === mPrime) {
      super.hash = -1
      this
    }
    else {
      SortedSet(mPrime)
    }
  }

  override fun <U> zip(f: Function<V?, U>): SortedMap<V?, U> {
    return m.mapValues { k: V, _: Void? -> f.apply(k) }
  }

  override fun valueHash(): ToLongFunction<V> {
    return m.keyHash()
  }

  override fun valueEquality(): BiPredicate<V, V> {
    return m.keyEquality()
  }

  override fun contains(value: V): Boolean {
    return m.contains(value)
  }

  override fun indexOf(element: V): OptionalLong {
    return m.indexOf(element)
  }

  override fun size(): Long {
    return m.size()
  }

  override fun nth(idx: Long): V {
    return m.nth(idx).key()
  }

  override fun elements(): IList<V> {
    return Lists.lazyMap<IEntry<V, Void?>, V>(m.entries()) { obj: IEntry<V, Void?> -> obj.key() }
  }

  override fun forked(): SortedSet<V> {
    return if (isLinear) SortedSet<V>(m.forked()) else this
  }

  override fun linear(): SortedSet<V> {
    return if (isLinear) this else SortedSet<V>(m.linear())
  }

  override fun isLinear(): Boolean {
    return m.isLinear
  }
}
