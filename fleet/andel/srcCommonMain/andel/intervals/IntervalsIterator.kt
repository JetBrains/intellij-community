// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

interface IntervalsIterator<T> {
  fun greedyLeft(): Boolean
  fun greedyRight(): Boolean
  fun from(): Long
  fun to(): Long
  fun id(): Long
  fun data(): T
  operator fun next(): Boolean

  fun toList(): List<Interval<Long, T>> {
    val list = ArrayList<Interval<Long, T>>()
    while (next()) {
      list.add(interval())
    }
    return list
  }

  fun interval(): Interval<Long, T> {
    return Interval(id(), from(), this.to(), greedyLeft(), greedyRight(), data())
  }

  companion object {
    @JvmField
    val FORWARD_COMPARATOR: Comparator<IntervalsIterator<*>> = compareBy { obj: IntervalsIterator<*> -> obj.from() }

    @JvmField
    val BACKWARD_COMPARATOR = FORWARD_COMPARATOR.reversed()

    @JvmStatic
    fun <T> toSequence(iteratorProvider: () -> IntervalsIterator<T>): Sequence<Interval<Long, T>> {
      return Sequence {
        val result = iteratorProvider.invoke()
        generateSequence {
          if (result.next()) result.interval() else null
        }.iterator()
      }
    }
  }
}