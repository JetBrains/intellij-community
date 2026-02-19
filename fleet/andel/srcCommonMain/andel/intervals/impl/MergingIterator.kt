// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals.impl

import andel.intervals.IntervalsIterator

internal class MergingIterator<T>(private var first: IntervalsIterator<T>,
                                  private var second: IntervalsIterator<T>?,
                                  private val comparator: Comparator<IntervalsIterator<*>>) : IntervalsIterator<T> {
  private var myFirstTime = true
  override fun greedyLeft(): Boolean {
    return first.greedyLeft()
  }

  override fun greedyRight(): Boolean {
    return first.greedyRight()
  }

  override fun from(): Long {
    return first.from()
  }

  override fun to(): Long {
    return first.to()
  }

  override fun id(): Long {
    return first.id()
  }

  override fun data(): T {
    return first.data()
  }

  override fun next(): Boolean {
    return if (myFirstTime) {
      myFirstTime = false
      val firstNext = first.next()
      val secondNext = second!!.next()
      if (firstNext && secondNext) {
        if (comparator.compare(first, second!!) > 0) {
          val tmp = second
          second = first
          first = tmp!!
        }
        true
      }
      else if (firstNext) {
        second = null
        true
      }
      else if (secondNext) {
        first = second!!
        second = null
        true
      }
      else {
        false
      }
    }
    else {
      if (first.next()) {
        if (second != null) {
          if (comparator.compare(first, second!!) > 0) {
            val tmp: IntervalsIterator<T> = second as IntervalsIterator<T>
            second = first
            first = tmp
          }
        }
        true
      }
      else {
        val sec = second
        if (sec == null) {
          false
        } else {
          first = sec
          second = null
          true
        }
      }
    }
  }
}