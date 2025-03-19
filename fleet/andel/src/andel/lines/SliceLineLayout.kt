// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

fun LinesLayout.slice(
  fromOffset: Long,
  toOffset: Long,
  slicePadding: LineBasedHeight = LineBasedHeight.ZERO,
  padFirst: Boolean = false,
  padLast: Boolean = false,
): LinesLayout {
  return SliceLineLayout(this, fromOffset, toOffset, if (padFirst) slicePadding else LineBasedHeight.ZERO, slicePadding, padFirst, padLast)
}

fun LinesLayout.slice(
  ranges: List<LongRange>,
  slicePadding: LineBasedHeight = LineBasedHeight.ZERO,
  padFirst: Boolean = false,
  padLast: Boolean = false,
): LinesLayout {
  if (ranges.isEmpty()) return this

  if (ranges.size == 1) return slice(ranges.first().first, ranges.first().last, slicePadding, padFirst, padLast)
  var actualPadding = LineBasedHeight.ZERO
  val slicesWithPaddings = ranges.mapIndexed { i, r ->
    val actualPadFirst = i > 0 || padFirst
    val actualPadLast = i == ranges.size - 1 && padLast
    if (actualPadFirst) {
      actualPadding += slicePadding
    }
    SliceLineLayout(this, r.first, r.last, actualPadding, slicePadding, actualPadFirst, actualPadLast) to actualPadding
  }
  val indices = ranges.indices.toList()
  val slices = slicesWithPaddings.sortedBy { it.first.fromLineIndex }
  val startLineIndices = mutableListOf<Long>()
  val heightAtFrom = mutableListOf<LineBasedHeight>()
  startLineIndices.add(0)
  heightAtFrom.add(LineBasedHeight.ZERO)
  for (i in (0 until ranges.size - 1)) {
    startLineIndices.add(startLineIndices[i] + slices[i].first.linesCount())
    heightAtFrom.add(heightAtFrom[i] + slices[i].first.lineTopAtTo - slices[i].first.lineTopAtFrom)
  }
  return object : LinesLayout {
    override fun preferredWidth(): Float {
      return slices.maxOf { it.first.preferredWidth() }
    }

    override fun lines(from: Long): Sequence<Line> {
      var index = findIndexByOffset(from)
      if (index < 0) index = kotlin.math.min(-index - 1, slices.size - 1)
      // todo: this code gets confused when original lines layout produces same line for different offsets
      // (e.g. due to foldings)
      return (index until slices.size).asSequence().map { i ->
        slices[i].first.lines(from).map { line ->
          line.toMyLine(i)
        }
      }.flatten()
    }

    override fun line(top: LineBasedHeight): Line {
      var index = findSliceByHeight(top)
      if (index < 0) index = kotlin.math.min(-index - 1, slices.size - 1)
      return slices[index].first.line(top - heightAtFrom[index]).toMyLine(index)
    }

    override fun line(offset: Long): Line {
      var index = findIndexByOffset(offset)
      if (index < 0) index = kotlin.math.min(-index - 1, slices.size - 1)
      return slices[index].first.line(offset).toMyLine(index)
    }

    override fun nth(lineId: Long): Line {
      TODO("Not yet implemented")
    }

    override fun linesCount(): Long {
      return startLineIndices.last() + slices.last().first.linesCount()
    }

    override fun linesHeight(): LineBasedHeight {
      return slices.sumOf { it.first.linesHeight() }
    }

    private fun findSliceByHeight(top: LineBasedHeight): Int = indices.binarySearch { i ->
      when {
        heightAtFrom[i] + slicesWithPaddings[i].second <= top && (i + 1 == ranges.size || top <= heightAtFrom[i + 1] + slicesWithPaddings[i + 1].second) -> 0
        top < heightAtFrom[i] + slicesWithPaddings[i].second -> 1
        else -> -1
      }
    }

    private fun findIndexByOffset(offset: Long): Int = indices.binarySearch { i ->
      when {
        slices[i].first.fromOffset <= offset && offset <= slices[i].first.toOffset -> 0
        offset < slices[i].first.fromOffset -> 1
        else -> -1
      }
    }

    private fun Line.toMyLine(index: Int): Line {
      return copy(lineTop = lineTop + heightAtFrom[index], lineIdx = lineIdx + startLineIndices[index])
    }
  }
}

internal class SliceLineLayout(
  val original: LinesLayout,
  val fromOffset: Long,
  val toOffset: Long,
  val fullPadding: LineBasedHeight,
  val slicePadding: LineBasedHeight,
  val padFirst: Boolean,
  val padLast: Boolean,
) : LinesLayout {
  val fromLine = original.line(fromOffset)
  val toLine = original.line(toOffset)
  val fromLineIndex = fromLine.lineIdx
  val toLineIndex = toLine.lineIdx
  val lineTopAtFrom = fromLine.lineTop
  val lineTopAtTo = toLine.lineTop + toLine.totalHeight

  override fun preferredWidth(): Float {
    return lines(fromOffset).maxOfOrNull { it.width } ?: original.preferredWidth()
  }

  override fun lines(from: Long): Sequence<Line> {
    val lines = original.lines(kotlin.math.max(from, fromOffset))
    return lines.takeWhile { it.lineIdx <= toLineIndex }.map { it.toMyLine() }
  }

  override fun line(top: LineBasedHeight): Line {
    return original.line(top + lineTopAtFrom - fullPadding).toMyLine()
  }

  override fun line(offset: Long): Line {
    return original.line(offset.coerceIn(fromOffset, toOffset)).toMyLine()
  }

  override fun nth(lineId: Long): Line {
    return original.nth(lineId + fromLineIndex).toMyLine()
  }

  override fun linesCount(): Long {
    return toLineIndex - fromLineIndex + 1
  }

  override fun linesHeight(): LineBasedHeight {
    val linesHeight = lineTopAtTo - lineTopAtFrom
    val topPadding = if (padFirst) slicePadding else LineBasedHeight.ZERO
    val bottomPadding = if (padLast) slicePadding else LineBasedHeight.ZERO
    return topPadding + linesHeight + bottomPadding
  }

  private fun Line.toMyLine(): Line {
    val topPadding = if (lineIdx == fromLineIndex && padFirst) slicePadding else LineBasedHeight.ZERO
    val bottomPadding = if (lineIdx == toLineIndex && padLast) slicePadding else LineBasedHeight.ZERO
    return copy(lineTop = lineTop - lineTopAtFrom + fullPadding - topPadding,
                totalHeight = totalHeight + topPadding + bottomPadding,
                lineIdx = lineIdx - fromLineIndex)
  }
}