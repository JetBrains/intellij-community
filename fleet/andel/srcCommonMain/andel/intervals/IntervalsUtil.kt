// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import andel.intervals.impl.KeyedIntervals

fun <K, T, R> IntervalsQuery<K, T>.mapValues(f: (T) -> R): IntervalsQuery<K, R> {
  val source = this
  return object : IntervalsQuery<K, R> {
    override fun findById(id: K): Interval<K, R>? {
      return source.findById(id)?.mapValues(f)
    }

    override fun query(start: Long, end: Long): Sequence<Interval<K, R>> {
      return source.query(start, end).map { it.mapValues(f) }
    }

    override fun queryReversed(start: Long, end: Long): Sequence<Interval<K, R>> {
      return source.queryReversed(start, end).map { it.mapValues(f) }
    }
  }
}


fun <K, T> IntervalsQuery<K, T>.getById(id: K): Interval<K, T> {
  return requireNotNull(findById(id))
}

fun <K : Any, T : Any> IntervalsFactory<Long>.fromIntervals(intervals: Iterable<Interval<K, T>>): Intervals<K, T> {
  return keyed<K>().empty<T>().addIntervals(intervals)
}

fun <K : Any> IntervalsFactory<Long>.keyed(): IntervalsFactory<K> {
  return object : IntervalsFactory<K> {
    override fun <T : Any> empty(): Intervals<K, T> = KeyedIntervals(this@keyed.empty())
  }
}


/**
 * adds only absent intervals to the tree
 */
fun <K, T> Intervals<K, T>.addAbsent(intervals: Iterable<Interval<K, T>>): Intervals<K, T> {
  return addIntervals(intervals.filter { findById(it.id) == null })
}

fun <T : Any> mergeSorted(comparator: Comparator<in T>, sequences: Collection<Sequence<T>>): Sequence<T> {
  return Sequence {
    val iterators = sequences.mapTo(ArrayList(sequences.size)) { it.iterator() }
    val next = iterators.mapTo(ArrayList(sequences.size)) { if (it.hasNext()) it.next() else null }
    generateSequence {
      next.indices.minWithOrNull { i, j ->
        nullsLast(comparator).compare(next[i], next[j])
      }?.let { nextIndex -> 
        next[nextIndex]?.also { 
          next[nextIndex] = if (iterators[nextIndex].hasNext()) iterators[nextIndex].next() else null
        }
      }
    }.iterator()
  }
}

fun <T : Any> Sequence<T>.toReversedList(): List<T> {
  val list = this.toMutableList()
  list.reverse()
  return list
}