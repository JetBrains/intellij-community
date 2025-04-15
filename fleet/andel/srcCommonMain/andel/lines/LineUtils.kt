// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.intervals.Interval
import andel.intervals.Intervals
import andel.intervals.IntervalsQuery
import andel.operation.Op
import andel.operation.Operation
import andel.operation.isIdentity
import andel.text.*
import fleet.util.CancellationToken
import fleet.util.takeWhileInclusive
import kotlin.math.min

fun <K, T> adjustIntervals(
  mergedIterator: Sequence<Interval<K, T>>,
  start: CharOffset,
  end: CharOffset,
): List<Interval<K, T>> {
  val intervals = ArrayList<Interval<K, T>>()
  for (interval in mergedIterator) {
    val from = (interval.from - start).coerceIn(0L, end - start)
    val to = (interval.to - start).coerceIn(0L, end - start)
    if (from != to || interval.from == interval.to || start == end) {
      intervals.add(interval.copy(from = from, to = to))
    }
  }
  return intervals
}

fun getInterlinesBelowLine(
  interlines: Sequence<Interval<*, Interline>>, line: TextRange, lastLine: Boolean,
  folds: Sequence<Interval<*, Fold>>,
): Sequence<Interval<*, Interline>> =
  interlines.filter {
    isBelow(line, it, lastLine) && !hasCoveringFoldRegion(folds, it.interlineOffset, false)
  }

fun isBelow(line: TextRange, interline: Interval<*, Interline>, lastLine: Boolean) =
  (line.start <= interline.to
   && (interline.to < line.end || (interline.to == line.end && line.isEmpty) || lastLine)
   && interline.data.binding == Interline.Binding.BelowLine)

fun getInterlinesAboveLine(
  interlines: Sequence<Interval<*, Interline>>,
  line: TextRange,
  lastLine: Boolean,
  folds: Sequence<Interval<*, Fold>>,
): Sequence<Interval<*, Interline>> =
  interlines.filter { isAbove(line, it, lastLine) && !hasCoveringFoldRegion(folds, it.interlineOffset, true) }

fun isAbove(line: TextRange, interline: Interval<*, Interline>, lastLine: Boolean) =
  (line.start <= interline.from
   && (interline.from < line.end || (interline.to == line.end && line.isEmpty) || lastLine)
   && interline.data.binding == Interline.Binding.AboveLine)

private val inlaysComparator: Comparator<Interval<*, Inlay>> = compareBy<Interval<*, Inlay>> { it.inlayOffset }
  .thenComparator { inlay1, inlay2 ->
    when (inlay1.data.binding) {
      inlay2.data.binding -> 0
      Inlay.Binding.BeforeRange -> -1
      Inlay.Binding.AfterRange -> 1
    }
  }

fun getLineInlays(
  inlays: IntervalsQuery<*, Inlay>,
  line: TextRange,
  isLastLine: Boolean,
  folds: Sequence<Interval<*, Fold>>,
): Sequence<Interval<*, Inlay>> {
  return inlays
    .query(line.start, line.end)
    .filter { inlay ->
      val offset = inlay.inlayOffset
      line.start <= offset && (offset < line.end || isLastLine)
    }
    .filter { !hasCoveringFoldRegion(folds, it.inlayOffset, it.data.binding == Inlay.Binding.BeforeRange) }
    .sortedWith(inlaysComparator)
}

fun getGutterWidgets(
  gutterWidgets: IntervalsQuery<*, GutterWidget>,
  line: TextRange,
  folds: Sequence<Interval<*, Fold>>,
  firstVisibleLine: Line?,
): Sequence<Interval<*, GutterWidget>> {
  return gutterWidgets
    .query(line.start, line.end)
    .filter { !hasCoveringFoldRegion(folds, it.from, true) }
    .filter {
      if (it.data.followOnScroll) {
        firstVisibleLine != null &&
        // bottom part of the fragment is in the top of the viewport
        (firstVisibleLine.from == line.start ||
         // middle of the viewport case
         line.start <= it.from && line.start >= firstVisibleLine.from)
      }
      else {
        // gutter widget should be rendered only on the first line of the fragment
        line.start <= it.from
      }
    }
}

fun hasCoveringFoldRegion(folds: Sequence<Interval<*, Fold>>, offset: Long, bindBefore: Boolean): Boolean =
  folds.any {
    when {
      it.from < offset && offset < it.to -> true
      !bindBefore && offset == it.from -> true
      bindBefore && offset == it.to -> true
      else -> false
    }
  }

internal fun computeInterlineHeights(
  interlines: IntervalsQuery<*, Interline>,
  foldRanges: Sequence<Interval<*, Fold>>,
  from: Long,
  to: Long,
  lastLine: Boolean,
): Pair<LineBasedHeight, LineBasedHeight> {
  val lineRange = TextRange(from, to)
  var interlineHeightAbove = LineBasedHeight.ZERO
  var interlineHeightBelow = LineBasedHeight.ZERO
  interlines.query(from, to).forEach { interval ->
    val above = isAbove(lineRange, interval, lastLine)
    val below = isBelow(lineRange, interval, lastLine)
    if ((above || below) && !hasCoveringFoldRegion(foldRanges, interval.interlineOffset, above)) {
      val height = interval.data.height
      require(height.ratio >= 0) { "negative interline height ${interval.id}" }
      if (above) {
        interlineHeightAbove += height
      }
      else {
        interlineHeightBelow += height
      }
    }
  }
  return interlineHeightAbove to interlineHeightBelow
}

fun buildLines(
  text: CharSequence,
  foldings: IntervalsQuery<*, Fold>,
  inlays: IntervalsQuery<*, Inlay>,
  inlayMeasurer: (Inlay) -> Float,
  foldsMeasurer: (Fold) -> Float,
  interlines: IntervalsQuery<*, Interline>,
  softWrapBuilder: SoftWrapBuilder,
  cancellationToken: CancellationToken,
): List<LineData> {
  val lines = ArrayList<LineData>()
  var offset = 0L
  var lineOffset = 0L
  val length = text.length.toLong()
  val hasFoldings = foldings.asIterable().iterator().hasNext()
  val folds = mutableListOf<Interval<*, Fold>>()
  while (offset < length) {
    val fold = if (hasFoldings) foldings.query(offset, offset).filter { it.from == offset }.maxByOrNull { it.to - it.from } else null
    fold?.let {
      offset = fold.to
      folds.add(fold)
    }
    if (offset < length && text[offset.toInt()] == '\n') {
      cancellationToken.checkCancelled()
      lines.addAll(
        softWrapBuilder.buildSoftLines(
          text = text,
          inlays = inlays,
          inlayMeasurer = inlayMeasurer,
          interlines = interlines,
          buildRange = TextRange(lineOffset, offset + 1),
          foldRanges = folds,
          foldsMeasurer = foldsMeasurer,
          lastLine = false
        )
      )
      folds.clear()
      lineOffset = offset + 1
    }
    offset++
  }
  val endLines = softWrapBuilder.buildSoftLines(
    text = text,
    inlays = inlays,
    inlayMeasurer = inlayMeasurer,
    interlines = interlines,
    buildRange = TextRange(lineOffset, length),
    foldRanges = folds,
    foldsMeasurer = foldsMeasurer,
    lastLine = true
  )
  lines.addAll(endLines)
  return lines
}


fun LinesCache.offsetOfWidth(
  text: CharSequence,
  range: TextRange,
  targetWidth: Float,  // in logical pixels
  inlays: IntervalsQuery<*, Inlay>,
  foldings: IntervalsQuery<*, Fold>,
): OffsetOfWidth {
  val folds = mutableListOf<Interval<*, Fold>>()
  var offset = range.start
  while (offset < range.end) {
    val fold = foldings.query(offset, offset).filter { it.from == offset }.maxByOrNull { it.to - it.from }
    if (fold != null) {
      offset = fold.to
      folds.add(fold)
    }
    else offset++
  }
  val data = softWrapBuilder.buildSoftLines(
    text = text,
    inlays = inlays,
    inlayMeasurer = inlayMeasurer,
    interlines = IntervalsQuery.empty<Any?, Interline>(),
    buildRange = range,
    foldRanges = folds,
    foldsMeasurer = foldsMeasurer,
    lastLine = false,
    targetWidth = targetWidth
  ).single()
  return OffsetOfWidth(data.length + range.start, data.width)
}

fun operationToRanges(
  before: Text,
  edit: Operation,
  linesLayout: LinesLayout
): Triple<List<TextRange>, List<TextRange>, List<TextRange>> {
  val lineRanges = mutableListOf<TextRange>()
  val beforeRanges = mutableListOf<TextRange>()
  val afterRanges = mutableListOf<TextRange>()

  val beforeText = before.view()
  var hardLineZipper = 0
  var beforeZipper = 0
  var offsetAfter = 0L
  for (op in edit.ops) {
    val offsetBefore = beforeZipper
    when (op) {
      is Op.Replace -> {
        hardLineZipper = beforeText.lineStartOffset(beforeText.lineAt(beforeZipper))
        beforeZipper += op.lenBefore.toInt()
        val visualLineRange = run {
          val visualFrom = linesLayout.line(beforeZipper.toLong())
            .takeIf { beforeZipper == 0 || beforeZipper.toLong() != it.from } ?:
            linesLayout.line(beforeZipper.toLong() - 1)
          val from = min(hardLineZipper.toLong(), visualFrom.from)
          val to = linesLayout.line(beforeText.lineEndOffset(beforeText.lineAt(beforeZipper)).toLong()).to
          TextRange(from, to)
        }
        if (lineRanges.lastOrNull()?.intersectsNonStrict(visualLineRange) == true) {
          lineRanges.add(TextRange(lineRanges.removeLast().start, visualLineRange.end))
          beforeRanges.add(TextRange(beforeRanges.removeLast().start, offsetBefore + op.lenBefore))
          afterRanges.add(TextRange(afterRanges.removeLast().start, offsetAfter + op.lenAfter))
        }
        else {
          lineRanges.add(visualLineRange)
          beforeRanges.add(TextRange(offsetBefore, offsetBefore + op.lenBefore.toInt()))
          afterRanges.add(TextRange(offsetAfter, offsetAfter + op.lenAfter))
        }
        offsetAfter += op.lenAfter
      }
      is Op.Retain -> {
        beforeZipper += op.len.toInt()
        offsetAfter += op.lenAfter
      }
    }
  }
  return Triple(lineRanges, beforeRanges, afterRanges)
}

fun LinesCache.edit(
  before: Text,
  after: Text,
  edit: Operation,
  newInlays: IntervalsQuery<*, Inlay>,
  newInterlines: IntervalsQuery<*, Interline>,
  newFolds: IntervalsQuery<*, Fold>,
): LinesCache {
  if (edit.isIdentity()) return this
  val (lineRanges, beforeRanges, afterRanges) = operationToRanges(
    before,
    edit,
    linesLayout())

  return rebuildLines(
    linesCache = this,
    before = before,
    after = after,
    edit = edit,
    lineRanges = lineRanges,
    beforeRanges = beforeRanges,
    afterRanges = afterRanges,
    newInlays = newInlays,
    newInterlines = newInterlines,
    newFolds = newFolds
  )
}

fun updateHeights(
  linesCache: LinesCache,
  interlines: IntervalsQuery<*, Interline>,
  folds: Sequence<Interval<*, Fold>>,
  sortedOffsets: Iterable<Long>,
): LinesCache {
  var linesCache = linesCache
  for (offset in sortedOffsets) {
    val line = linesCache.linesLayout().line(offset)
    val (interlineHeightAbove, interlineHeightBelow) = computeInterlineHeights(
      interlines = interlines,
      foldRanges = folds,
      from = line.from,
      to = line.to,
      lastLine = line.to == linesCache.textLength
    )
    require(interlineHeightAbove >= LineBasedHeight.ZERO && interlineHeightBelow >= LineBasedHeight.ZERO) {
      "attempt to set negative interline height at $line"
    }
    linesCache = linesCache.replaceLines(
      fromIndex = line.lineIdx,
      toIndex = line.lineIdx + 1,
      newLines = listOf(
        LineData(
          length = line.to - line.from,
          interlineHeightAbove = interlineHeightAbove,
          interlineHeightBelow = interlineHeightBelow,
          width = line.width
        )
      )
    )
  }
  return linesCache
}

fun updateInlayHints(
  linesCache: LinesCache,
  text: Text,
  invalidatedOffsets: List<Long>,
  newInlays: IntervalsQuery<*, Inlay>,
  newInterlines: IntervalsQuery<*, Interline>,
  newFolds: IntervalsQuery<*, Fold>,
): LinesCache {
  val linesLayout = linesCache.linesLayout()
  val lineRanges = invalidatedOffsets.map { linesLayout.line(it).let { l -> TextRange(l.from, l.to) } }.distinct()
  if (lineRanges.isEmpty()) return linesCache
  val ranges = lineRanges.map { TextRange(it.start, (it.end - 1).coerceAtLeast(it.start)) }
  return rebuildLines(
    linesCache = linesCache,
    before = text,
    after = text,
    edit = null,
    lineRanges = lineRanges,
    beforeRanges = ranges,
    afterRanges = ranges,
    newInlays = newInlays,
    newInterlines = newInterlines,
    newFolds = newFolds
  )
}

private fun rebuildLines(
  linesCache: LinesCache,
  before: Text,
  after: Text,
  edit: Operation?,
  lineRanges: List<TextRange>,
  beforeRanges: List<TextRange>,
  afterRanges: List<TextRange>,
  newInlays: IntervalsQuery<*, Inlay>,
  newInterlines: IntervalsQuery<*, Interline>,
  newFolds: IntervalsQuery<*, Fold>,
): LinesCache {
  var cache = linesCache
  val textBefore = before.view()
  val textAfter = after.view()
  var adjustment = 0L

  for (i in lineRanges.indices) {
    val lineRange = lineRanges[i]
    val beforeRange = beforeRanges[i]
    val afterRange = afterRanges[i]

    val prefix = textBefore.string(lineRange.start.toInt(), beforeRange.start.toInt())
    val insert = textAfter.string(afterRange.start.toInt(), afterRange.end.toInt())
    val suffix = textBefore.string(beforeRange.end.toInt(), lineRange.end.toInt())

    val currentAdjustment = (afterRange.end - afterRange.start) - (beforeRange.end - beforeRange.start)
    val adjustedRange = TextRange(lineRange.start + adjustment, (lineRange.end + adjustment + currentAdjustment).coerceAtLeast(lineRange.start + adjustment))

    val intersectingFoldings = newFolds.query(adjustedRange.start, adjustedRange.end)
    val lineFoldings = Intervals.droppingCollapsed().fromIntervals(adjustIntervals(intersectingFoldings, adjustedRange.start, adjustedRange.end) as ArrayList<Interval<Long, Fold>>)
    val lastLine = lineRange.end == before.charCount.toLong()
    val filteredInlays = getLineInlays(newInlays, adjustedRange, lastLine, intersectingFoldings)
    val lineInlays = Intervals.keepingCollapsed().fromIntervals(adjustIntervals(filteredInlays, adjustedRange.start, adjustedRange.end) as ArrayList<Interval<Long, Inlay>>)

    val intersectingInterlines = newInterlines.query(adjustedRange.start, adjustedRange.end)
    val filteredInterlines = sequenceOf(
      getInterlinesAboveLine(intersectingInterlines, adjustedRange, lastLine, intersectingFoldings),
      getInterlinesBelowLine(intersectingInterlines, adjustedRange, lastLine, intersectingFoldings)
    ).flatten()
    val lineInterlines = Intervals.keepingCollapsed().fromIntervals(adjustIntervals(filteredInterlines, adjustedRange.start, adjustedRange.end) as ArrayList<Interval<Long, Interline>>)

    val newText = prefix + insert + suffix
    val lines = buildLines(
      text = newText,
      foldsMeasurer = linesCache.foldsMeasurer,
      foldings = lineFoldings,
      inlays = lineInlays,
      inlayMeasurer = linesCache.inlayMeasurer,
      interlines = lineInterlines,
      softWrapBuilder = linesCache.softWrapBuilder,
      cancellationToken = CancellationToken.NonCancellable
    )
    val nonEmptyLines = when {
      newText.endsWith('\n') && suffix.endsWith('\n') && lines.last().length == 0L -> lines.take(lines.size - 1)
      else -> lines
    }
    val linesFrom = cache.linesLayout().lines(lineRange.start + adjustment)
    val untilOffset = lineRange.end + (if (beforeRange.end == lineRange.end) 1 else 0) + adjustment
    val someLines = linesFrom.takeWhileInclusive { line -> line.to < untilOffset }.toList()
    cache = cache.replaceLines(someLines.first().lineIdx, someLines.last().lineIdx + 1, nonEmptyLines)
    adjustment += currentAdjustment
  }
  val ropeLength = cache.textLength.toInt()
  require(ropeLength == after.charCount) {
    "${ropeLength} does not cover text length ${after.charCount} exactly after edit $edit\n${before.view().charSequence()}"
  }
  return cache
}
