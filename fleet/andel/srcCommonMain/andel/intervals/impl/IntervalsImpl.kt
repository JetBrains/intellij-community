// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals.impl

import andel.intervals.*
import andel.intervals.Intervals.Companion.CompareByStart
import andel.intervals.IntervalsIterator.Companion.toSequence
import andel.operation.Op
import andel.operation.Op.Retain
import andel.operation.Operation
import kotlinx.collections.immutable.PersistentMap

/**
 * Note: this is not a data class because we want to avoid expensive calculations in default equals()
 */
class IntervalsImpl<T : Any>(
  val maxChildren: Int,
  val openRoot: Impl.Node,
  val closedRoot: Impl.Node,
  val parentsMap: PersistentMap<Long, Long>,
  val nextInnerId: Long,
  val dropEmpty: Boolean,
) : Intervals<Long, T> {
  fun batch(): Impl.Batch<T> {
    return Impl.batch(this)
  }

  override fun findById(id: Long): Interval<Long, T>? {
    return Impl.getById(this, id)
  }

  override fun removeByIds(ids: Iterable<Long>): Intervals<Long, T> {
    return Impl.remove(this, ids)
  }

  // used in clojure tests via reflection
  @Suppress("MemberVisibilityCanBePrivate")
  fun queryForward(start: Long, end: Long): IntervalsIterator<T> {
    return Impl.query(this, start, end)
  }

  override fun query(start: Long, end: Long): Sequence<Interval<Long, T>> {
    return toSequence { queryForward(start, end) }
  }

  override fun queryReversed(start: Long, end: Long): Sequence<Interval<Long, T>> {
    return toSequence { queryBackward(start, end) }
  }

  override fun asIterable(): Iterable<Interval<Long, T>> {
    return query(0, Impl.MAX_VALUE).asIterable()
  }

  private fun queryBackward(start: Long, end: Long): IntervalsIterator<T> {
    return Impl.queryReverse(this, start, end)
  }

  fun expand(offset: Long, length: Long): IntervalsImpl<T> {
    return Impl.expand(this, offset, length)
  }

  fun collapse(offset: Long, length: Long): IntervalsImpl<T> {
    return Impl.collapse(this, offset, length)
  }

  override fun edit(edit: Operation): Intervals<Long, T> {
    var offset: Long = 0
    var res = this
    for (op in edit.ops) {
      if (op is Retain) {
        offset += op.len
      }
      else if (op is Op.Replace) {
        val insert = op.insert
        val length = insert.length
        res = res.expand(offset, length.toLong())
        offset += length.toLong()
        val delete = op.delete
        res = res.collapse(offset, delete.length.toLong())
      }
    }
    return res
  }

  override fun addIntervals(intervals: Iterable<Interval<Long, T>>): Intervals<Long, T> {
    val intervalsList = when (intervals) {
      is Collection -> ArrayList<Interval<Long, T>>(intervals)
      else -> intervals.toCollection(ArrayList())
    }
    return when {
      intervalsList.isEmpty() -> this
      else -> {
        intervalsList.sortWith(CompareByStart)
        val batch = batch()
        for ((id, from, to, greedyLeft, greedyRight, data) in intervalsList) {
          batch.add(id,
                    from, to,
                    greedyLeft, greedyRight,
                    data)
        }
        batch.commit()
      }
    }
  }

  override fun factory(): IntervalsFactory<Long> {
    return OldIntervalsFactory(dropEmpty)
  }

  override fun toString(): String {
    return queryForward(0, Impl.MAX_VALUE + 1).toList().toString()
  }
}