// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import kotlin.jvm.JvmInline

@JvmInline
value class LineBasedHeight(val ratio: Long) {
  companion object {
    val ZERO: LineBasedHeight = LineBasedHeight(0)
    val ONE_LINE = LineBasedHeight(100)
    fun fromRatio(f: Float): LineBasedHeight = LineBasedHeight((f * 100).toLong())
  }

  operator fun plus(other: LineBasedHeight): LineBasedHeight {
    return LineBasedHeight(ratio + other.ratio)
  }

  operator fun minus(other: LineBasedHeight): LineBasedHeight {
    return LineBasedHeight(ratio - other.ratio)
  }

  fun toRatio(): Float = ratio.toFloat() / 100

  operator fun compareTo(other: LineBasedHeight): Int {
    return ratio.compareTo(other.ratio)
  }

  fun toHeight(lineHeight: Float): Float = toRatio() * lineHeight
}

fun max(a: LineBasedHeight, b: LineBasedHeight) = LineBasedHeight(kotlin.math.max(a.ratio, b.ratio))
fun min(a: LineBasedHeight, b: LineBasedHeight) = LineBasedHeight(kotlin.math.min(a.ratio, b.ratio))
fun LineBasedHeight.coerceIn(min: LineBasedHeight, max: LineBasedHeight) = LineBasedHeight(ratio.coerceIn(min.ratio, max.ratio))
inline fun <T> Iterable<T>.sumOf(selector: (T) -> LineBasedHeight): LineBasedHeight {
  var sum = LineBasedHeight.ZERO
  for (element in this) {
    sum += selector(element)
  }
  return sum
}
