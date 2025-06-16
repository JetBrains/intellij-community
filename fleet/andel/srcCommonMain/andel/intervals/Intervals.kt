// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import andel.intervals.impl.Impl

interface Intervals<K, T> : IntervalsQuery<K, T> {
  fun addIntervals(intervals: Iterable<Interval<K, T>>): Intervals<K, T>

  fun removeByIds(ids: Iterable<K>): Intervals<K, T>

  fun edit(edit: andel.operation.Operation): Intervals<K, T>

  fun factory(): IntervalsFactory<K>

  companion object : OldIntervalsFactory(false) {
    override fun <T : Any> empty(): Intervals<Long, T> {
      return Impl.empty(false)
    }

    val CompareByStart: Comparator<Interval<*, *>> =
      compareBy<Interval<*, *>> { it.from }
        .thenBy { interval ->
          if (interval.greedyLeft) 1 else if (interval.from == interval.to) 0 else -1
        }

    val CompareByEnd: Comparator<Interval<*, *>> =
      compareBy<Interval<*, *>> { it.to }
        .thenBy { interval ->
          if (interval.greedyRight) 1 else if (interval.from == interval.to) 0 else -1
        }

    val QueryComparator: Comparator<Interval<*, *>> =
      compareBy { it.from }

    val ReverseQueryComparator: Comparator<Interval<*, *>> =
      compareByDescending { it.from }
  }
}
