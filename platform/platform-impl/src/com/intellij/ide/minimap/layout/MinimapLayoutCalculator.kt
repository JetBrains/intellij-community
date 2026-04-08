// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.layout

import com.intellij.ide.minimap.model.MinimapStructureMarker
import com.intellij.ide.minimap.model.MinimapLineProjection
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.render.MinimapRenderEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import java.awt.geom.Rectangle2D
import kotlin.math.ceil

class MinimapLayoutCalculator(private val editor: Editor) {
  private data class LineBand(
    val yOffset: Double,
    val height: Double,
  )

  private data class LayoutBuildState(
    val layout: MinimapLayoutContext,
    val documentLength: Int,
    val tokenEntries: ArrayList<MinimapRenderEntry>,
    val structureEntries: ArrayList<MinimapRenderEntry>,
  )

  fun buildLayout(
    context: MinimapRenderContext,
    structureMarkers: List<MinimapStructureMarker>,
    mode: MinimapLayoutMode,
  ): MinimapLayoutBuildResult = when (mode) {
    MinimapLayoutMode.EXACT -> buildExactLayout(context, structureMarkers)
    MinimapLayoutMode.DENSE -> buildDenseLayout(context, structureMarkers)
  }

  private fun buildExactLayout(
    context: MinimapRenderContext,
    structureMarkers: List<MinimapStructureMarker>,
  ): MinimapLayoutBuildResult {
    val prepared = prepareLayout(context, structureMarkers) ?: return MinimapLayoutBuildResult.EMPTY
    appendTokenFillers(prepared)
    appendStructureMarkers(prepared, structureMarkers)
    return buildResult(prepared)
  }

  private fun buildDenseLayout(
    context: MinimapRenderContext,
    structureMarkers: List<MinimapStructureMarker>,
  ): MinimapLayoutBuildResult {
    val prepared = prepareLayout(context, structureMarkers) ?: return MinimapLayoutBuildResult.EMPTY
    appendDenseFillers(prepared)
    appendStructureMarkers(prepared, structureMarkers)
    return buildResult(prepared)
  }

  private fun prepareLayout(
    context: MinimapRenderContext,
    structureMarkers: List<MinimapStructureMarker>,
  ): LayoutBuildState? {
    val geometry = context.geometry
    val panelWidth = context.panelWidth
    val minimapHeight = geometry.minimapHeight

    if (panelWidth <= 0 || minimapHeight <= 0) return null

    val document = editor.document
    val documentLength = document.textLength
    if (documentLength == 0) return null

    val metrics = MinimapLayoutUtil.computeLayoutMetrics(editor, context) ?: return null
    val lineCount = metrics.lineCount
    if (lineCount == 0) return null

    val tokenEntries = ArrayList<MinimapRenderEntry>()
    val structureEntries = ArrayList<MinimapRenderEntry>(structureMarkers.size)
    val visibleLines = MinimapLayoutUtil.visibleLines(geometry, lineCount)
    val layout = MinimapLayoutContext(
      document = document,
      metrics = metrics,
      areaStart = geometry.areaStart.toDouble(),
      visibleLines = visibleLines,
      lineProjection = context.lineProjection,
    )
    return LayoutBuildState(layout, documentLength, tokenEntries, structureEntries)
  }

  private fun buildResult(prepared: LayoutBuildState): MinimapLayoutBuildResult {
    return MinimapLayoutBuildResult(
      tokenEntries = prepared.tokenEntries,
      structureEntries = prepared.structureEntries,
      metrics = prepared.layout.metrics,
    )
  }

  private fun appendTokenFillers(prepared: LayoutBuildState) {
    val result = prepared.tokenEntries
    val context = prepared.layout
    val lineProjection = context.lineProjection
    val pxPerColumn = context.metrics.pxPerColumn
    val document = context.document
    val chars = document.charsSequence
    if (context.visibleLines.isEmpty()) return
    val firstLogicalLine = lineProjection.projectedToLogicalLine(context.visibleLines.first) ?: return
    val iterator = editor.highlighter.createIterator(document.getLineStartOffset(firstLogicalLine))

    for (projectedLine in context.visibleLines) {
      val logicalLine = lineProjection.projectedToLogicalLine(projectedLine) ?: continue
      val lineStartOffset = document.getLineStartOffset(logicalLine)
      val lineEndOffset = document.getLineEndOffset(logicalLine)
      while (!iterator.atEnd() && iterator.end <= lineStartOffset) {
        iterator.advance()
      }
      if (lineEndOffset <= lineStartOffset) continue

      val trimmedEndOffset = trimLineEnd(chars, lineStartOffset, lineEndOffset)
      if (trimmedEndOffset <= lineStartOffset) continue

      val band = getLineBand(projectedLine, projectedLine + 1, context) ?: continue

      while (!iterator.atEnd() && iterator.start < trimmedEndOffset) {
        val tokenStart = iterator.start.coerceAtLeast(lineStartOffset)
        val tokenEnd = iterator.end.coerceAtMost(trimmedEndOffset)

        if (tokenEnd > tokenStart && !isWhitespace(chars, tokenStart, tokenEnd)) {
          val startColumn = (tokenStart - lineStartOffset).coerceAtLeast(0)
          val endColumn = (tokenEnd - lineStartOffset).coerceAtLeast(startColumn + 1)
          val rect2d = rectForColumns(startColumn, endColumn, band, context, pxPerColumn)
          result.add(MinimapRenderEntry(null, rect2d, sampleOffset = tokenStart))
        }

        if (iterator.end <= trimmedEndOffset) {
          iterator.advance()
        }
        else {
          break
        }
      }
    }
  }

  private fun appendDenseFillers(prepared: LayoutBuildState) {
    val result = prepared.tokenEntries
    val context = prepared.layout
    val lineProjection = context.lineProjection
    val visibleLines = context.visibleLines
    if (visibleLines.isEmpty()) return

    val baseLineHeight = context.metrics.baseLineHeight
    if (baseLineHeight <= 0.0) return

    val linesPerPixel = 1.0 / baseLineHeight
    val lineStride = ceil(linesPerPixel).toInt().coerceAtLeast(1)

    var line = visibleLines.first
    val endLineExclusive = visibleLines.last + 1
    while (line < endLineExclusive) {
      val bandEndLine = (line + lineStride).coerceAtMost(endLineExclusive)
      val band = getLineBand(line, bandEndLine, context)
      if (band == null) {
        line = bandEndLine
        continue
      }
      appendDenseBandFillers(result, context, band, lineProjection, line, bandEndLine)
      line = bandEndLine
    }
  }

  private fun appendDenseBandFillers(result: MutableList<MinimapRenderEntry>,
                                     context: MinimapLayoutContext,
                                     band: LineBand,
                                     lineProjection: MinimapLineProjection,
                                     startLine: Int,
                                     endLineExclusive: Int) {
    val lineCount = endLineExclusive - startLine
    if (lineCount <= 0) return

    appendDenseSampleForLine(result, context, band, lineProjection, startLine)

    if (lineCount > 2) {
      appendDenseSampleForLine(result, context, band, lineProjection, startLine + lineCount / 2)
    }

    if (lineCount > 1) {
      appendDenseSampleForLine(result, context, band, lineProjection, endLineExclusive - 1)
    }
  }

  private fun appendDenseSampleForLine(result: MutableList<MinimapRenderEntry>,
                                       context: MinimapLayoutContext,
                                       band: LineBand,
                                       lineProjection: MinimapLineProjection,
                                       projectedLine: Int) {
    val logicalLine = lineProjection.projectedToLogicalLine(projectedLine) ?: return
    val document = context.document
    val chars = document.charsSequence
    val lineStartOffset = document.getLineStartOffset(logicalLine)
    val lineEndOffset = document.getLineEndOffset(logicalLine)
    if (lineEndOffset <= lineStartOffset) return

    val trimmedEndOffset = trimLineEnd(chars, lineStartOffset, lineEndOffset)
    if (trimmedEndOffset <= lineStartOffset) return

    val trimmedStartOffset = trimLineStart(chars, lineStartOffset, trimmedEndOffset)
    if (trimmedStartOffset >= trimmedEndOffset) return

    val startColumn = (trimmedStartOffset - lineStartOffset).coerceAtLeast(0)
    val endColumn = (trimmedEndOffset - lineStartOffset).coerceAtLeast(startColumn + 1)
    val rect2d = rectForColumns(startColumn, endColumn, band, context, context.metrics.pxPerColumn)
    val sampleOffset = denseSampleOffset(trimmedStartOffset, trimmedEndOffset)
    result.add(MinimapRenderEntry(null, rect2d, sampleOffset = sampleOffset))
  }

  private fun denseSampleOffset(startOffset: Int, endOffsetExclusive: Int): Int {
    return startOffset + (endOffsetExclusive - startOffset) / 2
  }

  private fun appendStructureMarkers(prepared: LayoutBuildState,
                                     structureMarkers: List<MinimapStructureMarker>) {
    if (structureMarkers.isEmpty()) return

    val result = prepared.structureEntries
    val context = prepared.layout
    val documentLength = prepared.documentLength
    val document = context.document
    val lineProjection = context.lineProjection
    val pxPerColumn = context.metrics.pxPerColumn
    val projectedLineCount = context.metrics.lineCount

    for (marker in structureMarkers) {
      val range = resolveRange(marker) ?: continue

      val startOffset = range.startOffset.coerceIn(0, documentLength)
      val endOffset = range.endOffset.coerceIn(startOffset, documentLength)
      val startLine = document.getLineNumber(startOffset)
      val projectedStartLine = lineProjection.logicalToProjectedLine(startLine) ?: continue
      val projectedEndLine = (projectedStartLine + 1).coerceAtMost(projectedLineCount)
      val band = getLineBand(projectedStartLine, projectedEndLine, context) ?: continue

      val lineStartOffset = document.getLineStartOffset(startLine)
      val lineEndOffset = document.getLineEndOffset(startLine)
      val endOffsetInLine = endOffset.coerceIn(startOffset, lineEndOffset)
      val startColumn = (startOffset - lineStartOffset).coerceAtLeast(0)
      val endColumn = (endOffsetInLine - lineStartOffset).coerceAtLeast(startColumn + 1)
      val rect2d = rectForColumns(startColumn, endColumn, band, context, pxPerColumn)
      result.add(MinimapRenderEntry(marker.element, rect2d, sampleOffset = startOffset))
    }
  }

  private fun getLineBand(startLine: Int, endLine: Int, context: MinimapLayoutContext): LineBand? {
    val band = MinimapLayoutUtil.lineBandRect(startLine, endLine, context.metrics.baseLineHeight, context.areaStart)
    return if (band.height <= 0.0) null else LineBand(band.y, band.height)
  }

  private fun getRectForLineBand(x1: Double, x2: Double, band: LineBand, context: MinimapLayoutContext): Rectangle2D.Double {
    val contentStartX = context.metrics.contentStartX
    val contentEndX = contentStartX + context.metrics.contentWidth
    val clampedX1 = x1.coerceIn(contentStartX, contentEndX - 1.0)
    val clampedX2 = x2.coerceIn(clampedX1 + 1.0, contentEndX)
    val width = (clampedX2 - clampedX1).coerceAtLeast(1.0)

    return Rectangle2D.Double(clampedX1, band.yOffset, width, band.height)
  }

  private fun rectForColumns(startColumn: Int,
                             endColumn: Int,
                             band: LineBand,
                             context: MinimapLayoutContext,
                             perChar: Double): Rectangle2D.Double {
    val contentStartX = context.metrics.contentStartX
    val contentEndX = contentStartX + context.metrics.contentWidth
    return if (perChar < 0) {
      getRectForLineBand(contentStartX, contentEndX, band, context)
    } else {
      getRectForLineBand(
        contentStartX + startColumn * perChar,
        contentStartX + endColumn * perChar,
        band,
        context,
      )
    }
  }

  private fun resolveRange(structureMarker: MinimapStructureMarker): TextRange? {
    val pointerRange = structureMarker.pointer?.range
    if (pointerRange != null) return TextRange(pointerRange.startOffset, pointerRange.endOffset)

    val rangeMarker = structureMarker.rangeMarker ?: return null
    if (!rangeMarker.isValid) return null

    return rangeMarker.textRange
  }

  // TODO: definitely there must be a platform solution for such ops
  private fun trimLineEnd(chars: CharSequence, start: Int, end: Int): Int {
    var index = end
    while (index > start && chars[index - 1].isWhitespace()) index--
    return index
  }

  private fun trimLineStart(chars: CharSequence, start: Int, end: Int): Int {
    var index = start
    while (index < end && chars[index].isWhitespace()) index++
    return index
  }

  private fun isWhitespace(chars: CharSequence, start: Int, end: Int): Boolean {
    for (index in start until end) {
      if (!chars[index].isWhitespace()) return false
    }
    return true
  }
}
