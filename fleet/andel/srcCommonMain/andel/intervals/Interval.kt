// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.intervals

import andel.text.TextRange
import kotlin.jvm.JvmField

data class Interval<out K, out T>(
  @JvmField val id: K,
  @JvmField val from: Long,
  @JvmField val to: Long,
  @JvmField val greedyLeft: Boolean,
  @JvmField val greedyRight: Boolean,
  @JvmField val data: T,
) {

  constructor(
    id: K,
    range: TextRange,
    greedyLeft: Boolean,
    greedyRight: Boolean,
    data: T
  ) : this(id, range.start, range.end, greedyLeft, greedyRight, data)

  override fun toString(): String {
    return "id=" + id + " " + (if (greedyLeft) "[" else "(") + from + ", " + to + (if (greedyRight) "]" else ")") + " " + data
  }

  init {
    require(from == -1L || to == -1L || from <= to) {
      "Interval to <= from: $to <= $from"
    }
  }

  operator fun contains(other: Interval<Long, *>): Boolean {
    return other.from >= from && other.to <= to
  }
}

fun <K, T, R> Interval<K, T>.mapValues(f: (T) -> R): Interval<K, R> {
  return Interval(id = id,
                  from = from,
                  to = to,
                  greedyLeft = greedyLeft,
                  greedyRight = greedyRight,
                  data = f(data))
}