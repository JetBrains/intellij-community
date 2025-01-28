// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import andel.intervals.impl.Impl
import andel.text.TextRange

interface IntervalsFactory<K> {
  fun <T : Any> empty(): Intervals<K, T>
  fun <T : Any> fromIntervals(intervals: Iterable<Interval<K, T>>): Intervals<K, T> {
    return empty<T>().addIntervals(intervals)
  }

  fun <T : Any> fromSingle(identity: K,
                           range: TextRange,
                           greedyLeft: Boolean,
                           greedyRight: Boolean,
                           value: T): Intervals<K, T> {
    return this.fromIntervals(listOf(Interval(identity, range, greedyLeft, greedyRight, value)))
  }

  fun <T : Any> fromSingle(from: Long,
                           toInclusive: Long,
                           identity: K,
                           value: T,
                           greedyLeft: Boolean = true,
                           greedyRight: Boolean = true): Intervals<K, T> {
    return this.fromSingle(identity, TextRange(from, toInclusive), greedyLeft, greedyRight, value)
  }
}

open class OldIntervalsFactory(private val dropCollapsed: Boolean) : IntervalsFactory<Long> {
  override fun <T : Any> empty(): Intervals<Long, T> = Impl.empty(dropCollapsed)
  fun keepingCollapsed(): IntervalsFactory<Long> = OldIntervalsFactory(dropCollapsed = false)
  fun droppingCollapsed(): IntervalsFactory<Long> = OldIntervalsFactory(dropCollapsed = true)
}