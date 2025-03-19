// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals.impl

import andel.intervals.*
import andel.operation.Operation
import fleet.multiplatform.shims.AtomicRef
import fleet.util.incrementAndGet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf

class KeyedIntervals<K: Any, V>(val intervals: Intervals<Long, Pair<K, V>>,
                                val keys: PersistentMap<K, Long> = persistentHashMapOf()): Intervals<K, V> {

  companion object {
    fun<K, V> toInner(id: Long, i: Interval<K, V>): Interval<Long, Pair<K, V>> {
      return Interval(id, i.from, i.to, i.greedyLeft, i.greedyRight, i.id to i.data)
    }

    fun<K, V> fromInner(i: Interval<Long, Pair<K, V>>): Interval<K, V> {
      return Interval(i.data.first, i.from, i.to, i.greedyLeft, i.greedyRight, i.data.second)
    }

    val nextId: AtomicRef<Long> = AtomicRef(0)
  }

  override fun factory(): IntervalsFactory<K> {
    return intervals.factory().keyed()
  }

  override fun findById(id: K): Interval<K, V>? {
    return keys.get(id)?.let { intKey ->
      fromInner(intervals.getById(intKey))
    }
  }

  override fun query(start: Long, end: Long): Sequence<Interval<K, V>> {
    return intervals.query(start, end).map { interval -> fromInner(interval) }
  }

  override fun queryReversed(start: Long, end: Long): Sequence<Interval<K, V>> {
    return intervals.queryReversed(start, end).map { interval -> fromInner(interval) }
  }

  override fun addIntervals(intervals: Iterable<Interval<K, V>>): Intervals<K, V> {
    val inner = intervals.map { interval ->
      toInner(nextId.incrementAndGet(), interval)
    }
    return KeyedIntervals(intervals = this.intervals.addIntervals(inner),
                          keys = keys.builder().apply {
                            for (interval in inner) {
                              put(interval.data.first, interval.id)
                            }
                          }.build())
  }

  override fun removeByIds(ids: Iterable<K>): Intervals<K, V> {
    return KeyedIntervals(intervals = this.intervals.removeByIds(ids.mapNotNull { id -> keys.get(id)}),
                          keys = keys.builder().apply {
                            for (id in ids) {
                              remove(id)
                            }
                          }.build())
  }

  override fun edit(edit: Operation): Intervals<K, V> {
    return KeyedIntervals(intervals = intervals.edit(edit), keys = keys)
  }
}