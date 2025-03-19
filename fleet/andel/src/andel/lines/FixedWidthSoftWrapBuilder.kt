// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.lines

import andel.intervals.Interval
import andel.intervals.IntervalsQuery
import andel.text.*
import fleet.util.text.CodepointClass
import fleet.util.text.codepointClass
import fleet.util.text.isFullWidth

data class FixedWidthSoftWrapBuilder(
  val width: Float = Float.POSITIVE_INFINITY,
  val charWidth: Float = 6f,
  /** If possible, soft wrap will occur at a word boundary. */
  val preferWrapByWords: Boolean = true,
) : SoftWrapBuilder {
  override fun buildSoftLines(
    text: CharSequence,
    inlays: IntervalsQuery<*, Inlay>,
    inlayMeasurer: (Inlay) -> Float,
    interlines: IntervalsQuery<*, Interline>,
    buildRange: TextRange,
    foldRanges: List<Interval<*, Fold>>,
    foldsMeasurer: (Fold) -> Float,
    lastLine: Boolean,
    targetWidth: Float,
  ): List<LineData> {
    val lines = ArrayList<LineData>()
    val inlayWidths = getLineInlays(inlays, buildRange, lastLine, foldRanges.asSequence())
      .map { inlay ->
        inlay.inlayOffset to inlayMeasurer(inlay.data)
      }
      .toList()

    var inlayIndex = 0
    var foldIndex = 0
    var lineWidth = 0F
    var lineOffset = buildRange.start
    val visualIndent = text.subSequence(buildRange.start.toInt(), buildRange.end.toInt())
      .takeWhile { it == ' ' || it == '\t' }
      .sumOf { charGeomLength(it).toDouble() * charWidth }.toFloat().takeIf { it < width } ?: 0F
    var offset = buildRange.start
    var lastBreakOpportunity = -1L
    var breakWidth = 0F
    var canBrakeAfter = false
    val length = text.length.toLong()
    while (offset < buildRange.end && offset < text.length) {
      if (foldIndex < foldRanges.size && offset >= foldRanges[foldIndex].from) {
        offset = foldRanges[foldIndex].to
        lineWidth += foldsMeasurer(foldRanges[foldIndex].data)
        foldIndex++
      }
      if (offset >= text.length) break
      val codepoint = text[offset.toInt()]
      if (preferWrapByWords) {
        val codepointClass = codepointClass(codepoint.code)
        if (codepointClass == CodepointClass.SEPARATOR || codepointClass == CodepointClass.SPACE || canBrakeAfter) {
          lastBreakOpportunity = offset
          canBrakeAfter = codepointClass == CodepointClass.SEPARATOR || codepointClass == CodepointClass.SPACE
          breakWidth = lineWidth
        }
      }
      while (inlayIndex < inlayWidths.size && inlayWidths[inlayIndex].first == offset) {
        lineWidth += inlayWidths[inlayIndex].second
        inlayIndex++
      }
      val charSize = charGeomLength(codepoint) * charWidth
      if (lineWidth + charSize >= targetWidth) {
        val (breakPoint, appliedBreakWidth) = when {
          lineWidth + charSize - targetWidth > targetWidth - lineWidth -> offset to breakWidth
          else -> offset + 1 to lineWidth
        }
        val line = buildLine(interlines, foldRanges.asSequence(), lineOffset, breakPoint, appliedBreakWidth, buildRange.end == length && lastLine)
        return listOf(line)
      }
      val maxWidth = if (lines.isEmpty()) width else width - visualIndent
      if (lineWidth + charSize >= maxWidth) {
        val (breakPoint, appliedBreakWidth) = when {
          lastBreakOpportunity > 0 -> lastBreakOpportunity to breakWidth
          else -> offset to lineWidth
        }
        val line = buildLine(interlines, foldRanges.asSequence(), lineOffset, breakPoint, appliedBreakWidth, buildRange.end == length && lastLine)
        lines.add(line)
        lineOffset = breakPoint
        lineWidth -= appliedBreakWidth
        lastBreakOpportunity = -1
        canBrakeAfter = false
      }
      lineWidth += charSize
      offset++
    }
    val line = buildLine(interlines, foldRanges.asSequence(), lineOffset, buildRange.end, lineWidth, lastLine)
    lines.add(line)
    return lines
  }

  override fun clone(width: Float, charWidth: Float): SoftWrapBuilder {
    return this.copy(width = width, charWidth = charWidth)
  }

  override fun wrappingChanged(width: Float, charWidth: Float): Boolean = this.width != width || this.charWidth != charWidth
}

//todo this is wrong
internal fun charGeomLength(char: Char): Float {
  return when {
    char == '\n' -> 0f
    char == '\r' -> 0f
    char == '\t' -> 4f
    char.isLowSurrogate() -> 0f
    isFullWidth(char.code) -> 1.65f
    else -> 1f
  }
}