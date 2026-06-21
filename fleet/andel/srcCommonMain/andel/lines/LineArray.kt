// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.rope.Metric
import andel.util.chunked
import andel.util.replace
import kotlin.jvm.JvmInline
import kotlin.math.max

@JvmInline
value class LineArray internal constructor(val ints: IntArray) {
  companion object {
    fun fromLines(data: List<LineData>): LineArray =
      LineArray(
        IntArray(Fields * data.size).also { ints ->
          data.forEachIndexed { i, d ->
            ints[i * Fields + LengthField] = d.length.toInt()
            ints[i * Fields + InterlineHeightAboveField] = d.interlineHeightAbove.ratio.toInt()
            ints[i * Fields + InterlineHeightBelowField] = d.interlineHeightBelow.ratio.toInt()
            ints[i * Fields + OwnHeightField] = d.ownHeight.ratio.toInt()
            ints[i * Fields + WidthField] = d.width.toInt()
          }
        }
      )

    const val LengthField = 0
    const val InterlineHeightAboveField = 1
    const val InterlineHeightBelowField = 2
    const val OwnHeightField = 3
    const val WidthField = 4

    internal const val Fields = 5

    val Empty: LineArray = LineArray(intArrayOf())
  }

  fun metric(i: Int, metric: Metric): Int =
    when (metric) {
      LinesMonoid.CountMetric -> 1
      LinesMonoid.HeightMetric -> totalHeight(i)
      LinesMonoid.LengthMetric -> length(i)
      LinesMonoid.WidthMetric -> width(i)
      else -> error("metric is not defined")
    }

  private fun field(i: Int, field: Int): Int =
    ints[i * Fields + field]

  fun length(i: Int): Int =
    field(i, LengthField)

  fun interlineHeightAbove(i: Int): Int =
    field(i, InterlineHeightAboveField)

  fun interlineHeightBelow(i: Int): Int =
    field(i, InterlineHeightBelowField)

  /**
   * Height of the line itself (without interlines), [LineBasedHeight.ONE_LINE] unless the line is scaled
   * by a [LineScale].
   */
  fun ownHeight(i: Int): Int =
    field(i, OwnHeightField)

  fun totalHeight(i: Int): Int =
    interlineHeightAbove(i) + ownHeight(i) + interlineHeightBelow(i)

  fun width(i: Int): Int =
    field(i, WidthField)

  val size: Int
    get() = ints.size / Fields

  fun merge(rhs: LineArray): LineArray =
    LineArray(ints + rhs.ints)

  fun chunked(chunk: Int): List<LineArray> =
    ints.chunked(chunk * Fields).map(::LineArray)

  fun metrics(): LineArrayMetrics {
    var length = 0
    var height = 0
    var width = 0
    for (i in 0 until size) {
      length += length(i)
      height += totalHeight(i)
      width = max(width, width(i))
    }
    return LineArrayMetrics(
      length = length,
      height = height,
      count = size,
      width = width
    )
  }


  fun replaceLines(fromIndex: Int, toIndex: Int, newLines: LineArray): LineArray =
    LineArray(ints.replace(fromIndex * Fields, toIndex * Fields, newLines.ints))
}

data class LineArrayMetrics(
  val length: Int,
  val height: Int,
  val count: Int,
  val width: Int,
)
