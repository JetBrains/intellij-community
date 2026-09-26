// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals.impl

import andel.intervals.Interval
import andel.intervals.Intervals
import andel.intervals.IntervalsQuery
import andel.intervals.mergeSorted

internal data class IntervalsQuerySum<K, T>(
  val queries: List<IntervalsQuery<K, T>>,
) : IntervalsQuery<K, T> {
  companion object {

  }

  override fun findById(id: K): Interval<K, T>? =
    queries.firstNotNullOfOrNull { it.findById(id) }

  override fun query(start: Long, end: Long): Sequence<Interval<K, T>> = 
    mergeSorted(Intervals.QueryComparator, queries.map { it.query(start, end) })

  override fun queryReversed(start: Long, end: Long): Sequence<Interval<K, T>> =
    mergeSorted(Intervals.ReverseQueryComparator, queries.map { it.queryReversed(start, end) })

  override fun plus(other: IntervalsQuery<K, T>): IntervalsQuery<K, T> =
    when {
      other is IntervalsQuerySum<K, T> -> IntervalsQuerySum(queries + other.queries)
      else -> IntervalsQuerySum(queries + listOf(other))
    }
}