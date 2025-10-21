// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.intervals.Interval
import andel.intervals.Intervals
import andel.intervals.IntervalsQuery
import andel.rope.Metric
import andel.rope.Rope
import andel.text.Text
import andel.text.charSequence
import kotlin.math.min
import fleet.util.CancellationToken

data class OffsetOfWidth(val offset: Long, val width: Float)

data class LinesCache internal constructor(
  internal val rope: LinesRope,
  val inlayMeasurer: (Inlay) -> Float,
  val foldsMeasurer: (Fold) -> Float,
  val softWrapBuilder: SoftWrapBuilder,
) {

  fun linesLayout(): LinesLayout = LinesCacheLinesLayout(this)

  fun withSoftWrapBuilder(softWrapBuilder: SoftWrapBuilder): LinesCache =
    copy(softWrapBuilder = softWrapBuilder)

  val textLength: Long
    get() = rope.size(LinesMonoid.LengthMetric).toLong()

  fun replaceLines(
    fromIndex: Long,
    toIndex: Long,
    newLines: List<LineData>,
  ): LinesCache {
    var cursor = rope.cursor(this).scan(Any(), LinesMonoid.CountMetric, fromIndex.toInt())
    val leaf = cursor.element
    val count = (toIndex - fromIndex).toInt()
    val fromIndex = fromIndex.toInt() - cursor.location(LinesMonoid.CountMetric)
    val toIndex = min(leaf.size, fromIndex + count)
    val leafPrime = leaf.replaceLines(
      fromIndex = fromIndex,
      toIndex = toIndex,
      newLines = LineArray.fromLines(newLines)
    )
    cursor = cursor.replace(this, leafPrime)
    val replaced = toIndex - fromIndex
    var remaining = count - replaced
    while (remaining > 0) {
      val next = cursor.next(this)!!
      val leaf = next.element
      val toIndex = min(remaining, leaf.size)
      val nextPrime = next.replace(this, leaf.replaceLines(0, toIndex, LineArray.Empty))
      remaining = remaining - toIndex
      cursor = nextPrime
    }
    return LinesCache(
      rope = cursor.rope(this),
      inlayMeasurer = inlayMeasurer,
      foldsMeasurer = foldsMeasurer,
      softWrapBuilder = softWrapBuilder
    )
  }
}

class LinesCacheLinesLayout(val linesCache: LinesCache) : LinesLayout {
  private var _cursor = linesCache.rope.cursor(Any())

  private fun moveTo(metric: Metric, value: Int): Rope.Cursor<LineArray> =
    _cursor.scan(Any(), metric, value).also { cur ->
      _cursor = cur
    }

  override fun preferredWidth(): Float =
    linesCache.rope.size(LinesMonoid.WidthMetric).toFloat()

  override fun linesHeight(): LineBasedHeight =
    LineBasedHeight(linesCache.rope.size(LinesMonoid.HeightMetric).toLong())

  override fun linesCount(): Long =
    linesCache.rope.size(LinesMonoid.CountMetric).toLong()

  override fun lines(from: Long): Sequence<Line> =
    sequence {
      var cur = moveTo(LinesMonoid.LengthMetric, from.toInt())
      var lines = cur.element
      var offset = cur.location(LinesMonoid.LengthMetric)
      var top = cur.location(LinesMonoid.HeightMetric)
      var index = cur.location(LinesMonoid.CountMetric)
      var indexInLeaf = 0
      while (indexInLeaf < lines.size - 1) {
        val len = lines.length(indexInLeaf)
        if (from < offset + len) break
        offset = offset + len
        top = top + lines.totalHeight(indexInLeaf)
        indexInLeaf++
      }
      while (true) {
        while (indexInLeaf < lines.size) {
          val len = lines.length(indexInLeaf)
          val lineHeight = lines.totalHeight(indexInLeaf)
          yield(Line(
            from = offset.toLong(),
            to = (offset + len).toLong(),
            lineTop = LineBasedHeight(top.toLong()),
            totalHeight = LineBasedHeight(lineHeight.toLong()),
            interlineHeightAbove = LineBasedHeight(lines.interlineHeightAbove(indexInLeaf).toLong()),
            interlineHeightBelow = LineBasedHeight(lines.interlineHeightBelow(indexInLeaf).toLong()),
            lineIdx = (indexInLeaf + index).toLong(),
            width = lines.width(indexInLeaf).toFloat())
          )
          offset = offset + len
          top = top + lineHeight
          indexInLeaf++
        }
        indexInLeaf = 0
        cur = cur.next(Any()) ?: break
        index = cur.location(LinesMonoid.CountMetric)
        lines = cur.element
      }
    }

  override fun line(targetHeight: LineBasedHeight): Line =
    lineAt(LinesMonoid.HeightMetric, targetHeight.ratio.toInt())

  fun lineAt(metric: Metric, value: Int): Line {
    val cur = moveTo(metric, value)
    val lines = cur.element
    var offset = cur.location(LinesMonoid.LengthMetric)
    var top = cur.location(LinesMonoid.HeightMetric)
    val index = cur.location(LinesMonoid.CountMetric)
    var indexInLeaf = 0
    var x = cur.location(metric)
    while (indexInLeaf < lines.size) {
      val len = lines.length(indexInLeaf)
      val height = lines.totalHeight(indexInLeaf)
      val size = lines.metric(indexInLeaf, metric)
      if (value < x + size || indexInLeaf + 1 == lines.size) {
        return Line(
          from = offset.toLong(),
          to = (offset + len).toLong(),
          lineTop = LineBasedHeight(top.toLong()),
          totalHeight = LineBasedHeight(height.toLong()),
          interlineHeightAbove = LineBasedHeight(lines.interlineHeightAbove(indexInLeaf).toLong()),
          interlineHeightBelow = LineBasedHeight(lines.interlineHeightAbove(indexInLeaf).toLong()),
          lineIdx = (index + indexInLeaf).toLong(),
          width = lines.width(indexInLeaf).toFloat(),
        )
      }
      x = x + size
      offset = offset + len
      top = top + height
      indexInLeaf++
    }
    throw NoSuchElementException()
  }

  override fun line(offset: Long): Line =
    lineAt(LinesMonoid.LengthMetric, offset.toInt())

  override fun nth(lineId: Long): Line =
    lineAt(LinesMonoid.CountMetric, lineId.toInt())
}

fun buildLine(
  interlines: IntervalsQuery<*, Interline>,
  foldRanges: Sequence<Interval<*, Fold>>,
  offset: Long,
  nextLineStartOffset: Long,
  width: Float,
  lastLine: Boolean,
): LineData {
  val (interlineHeightAbove, interlineHeightBelow) = computeInterlineHeights(interlines, foldRanges, offset, nextLineStartOffset, lastLine)
  return LineData(length = nextLineStartOffset - offset,
                  interlineHeightAbove = interlineHeightAbove,
                  interlineHeightBelow = interlineHeightBelow,
                  width = width)
}

fun layoutLines(
  text: Text,
  inlays: IntervalsQuery<*, Inlay> = Intervals.droppingCollapsed().empty(),
  interlines: IntervalsQuery<*, Interline> = Intervals.droppingCollapsed().empty(),
  inlayMeasurer: (Inlay) -> Float = { 0F },
  folds: IntervalsQuery<*, Fold> = Intervals.droppingCollapsed().empty(),
  foldsMeasurer: (Fold) -> Float = { 0F },
  softWrapBuilder: SoftWrapBuilder,
  cancellationToken: CancellationToken = CancellationToken,
): LinesCache {
  val lines = buildLines(
    text = text.view().charSequence(),
    foldings = folds,
    inlays = inlays,
    inlayMeasurer = inlayMeasurer,
    interlines = interlines,
    foldsMeasurer = foldsMeasurer,
    softWrapBuilder = softWrapBuilder,
    cancellationToken = cancellationToken
  )
  return buildLinesCache(
    lines,
    inlayMeasurer = inlayMeasurer,
    foldsMeasurer = foldsMeasurer,
    softWrapBuilder = softWrapBuilder
  )
}

fun buildLinesCache(
  lines: List<LineData>,
  inlayMeasurer: (Inlay) -> Float,
  foldsMeasurer: (Fold) -> Float,
  softWrapBuilder: SoftWrapBuilder,
): LinesCache = LinesCache(
  rope = LinesMonoid.ropeOf(listOf(LineArray.fromLines(lines))),
  inlayMeasurer = inlayMeasurer,
  foldsMeasurer = foldsMeasurer,
  softWrapBuilder = softWrapBuilder,
)