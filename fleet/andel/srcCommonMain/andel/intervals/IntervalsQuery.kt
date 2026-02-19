// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import andel.intervals.impl.IntervalsQuerySum
import andel.operation.Operation
import andel.operation.Sticky
import andel.operation.invert
import andel.operation.transformOnto

interface IntervalsQuery<K, T> {
  companion object {

    fun<K, T> impl(q: (Long, Long) -> Sequence<Interval<K, T>>) = object: IntervalsQuery<K, T> {
      override fun findById(id: K): Interval<K, T> = error("there is no identity to intervals in this IQ")

      override fun query(start: Long, end: Long): Sequence<Interval<K, T>> = q(start, end)
    }

    val EMPTY = object : IntervalsQuery<Any, Any> {
      override fun findById(id: Any): Interval<Any, Any>? {
        return null
      }

      override fun query(start: Long, end: Long): Sequence<Interval<Any, Any>> {
        return emptySequence()
      }

      override fun queryReversed(start: Long, end: Long): Sequence<Interval<Any, Any>> {
        return emptySequence()
      }
    }

    @Suppress("UNCHECKED_CAST")
    fun <K, T> empty(): IntervalsQuery<K, T> = EMPTY as IntervalsQuery<K, T>
  }

  fun findById(id: K): Interval<K, T>?

  fun query(start: Long, end: Long): Sequence<Interval<K, T>>

  fun queryReversed(start: Long, end: Long): Sequence<Interval<K, T>> =
    query(start, end).sortedWith(Intervals.ReverseQueryComparator)

  fun asIterable(): Iterable<Interval<K, T>> = query(0, Long.MAX_VALUE).asIterable()
  
  operator fun plus(other: IntervalsQuery<K, T>): IntervalsQuery<K, T> =
    when {
      other == IntervalsQuery.empty<K, T>() -> this
      other is IntervalsQuerySum<K, T> -> other.copy(listOf(this) + other.queries)
      else -> IntervalsQuerySum(listOf(this, other)) 
    }
}

fun <K, T> IntervalsQuery<K, T>.adjust(operation: Operation): IntervalsQuery<K, T> {
  val invertedOperation = operation.invert()

  return IntervalsQuery.impl { start, end ->
    val adjustedStart = start.transformOnto(invertedOperation, Sticky.LEFT)
    val adjustedEnd = end.transformOnto(invertedOperation, Sticky.RIGHT)

    this.query(adjustedStart, adjustedEnd).map { interval ->
      val newFrom = interval.from.transformOnto(operation, if (interval.greedyLeft) Sticky.LEFT else Sticky.RIGHT)
      // this asymmetry is aligned with andel.intervals.impl.IntervalsImpl.edit (see andel.intervals.impl.Impl.expand)
      val newTo = maxOf(newFrom, interval.to.transformOnto(operation, if (interval.greedyRight) Sticky.RIGHT else Sticky.LEFT))
      interval.copy(from = newFrom, to = newTo)
    }
  }
}
