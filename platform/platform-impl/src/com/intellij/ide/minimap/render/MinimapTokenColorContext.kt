// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.render

import com.intellij.ide.minimap.layout.MinimapLayoutMetrics
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.HashMap

class MinimapTokenColorContext(
  renderContext: MinimapRenderContext,
  private val metrics: MinimapLayoutMetrics?,
) {
  private val areaStart = renderContext.geometry.areaStart.toDouble()
  private val document = renderContext.editor.document
  private val editorEx = renderContext.editor as? EditorEx
  private val highlighter = renderContext.editor.highlighter

  private val scheme = renderContext.editor.colorsScheme
  private val defaultForeground = scheme.defaultForeground
  private val background = scheme.defaultBackground

  private data class ColorRun(
    val startOffset: Int,
    val endOffset: Int,
    val color: Color,
  )

  private val keyColorCache = HashMap<TextAttributesKey, Color?>()
  private val lineColorRunsCache = HashMap<Int, List<ColorRun>>()
  private val softenedColorsCache = HashMap<Color, JBColor>()

  fun colorFor(entry: MinimapRenderEntry): JBColor {
    val metrics = metrics ?: return JBColor.GRAY
    if (metrics.lineCount <= 0) return JBColor.GRAY

    val baseColor = entry.color ?: resolveEntryColor(entry, metrics)
    return softenedColorsCache.getOrPut(baseColor) {
      val softened = softenColor(baseColor, background)
      JBColor(softened, softened)
    }
  }

  private fun resolveEntryColor(entry: MinimapRenderEntry, metrics: MinimapLayoutMetrics): Color {
    val offset = if (entry.sampleOffset != MinimapRenderEntry.NO_SAMPLE_OFFSET) {
      entry.sampleOffset
    }
    else {
      offsetFromRect(entry, metrics) ?: return defaultForeground
    }
    return colorAtOffset(offset)
  }

  private fun colorAtOffset(offset: Int): Color {
    colorFromMergedAttributes(offset)?.let { return it }

    val boundedOffset = offset.coerceIn(0, document.textLength)
    val iterator = highlighter.createIterator(boundedOffset)
    val keyColor = iterator.textAttributesKeys.firstNotNullOfOrNull { key ->
      keyColorCache.getOrPut(key) { scheme.getAttributes(key)?.foregroundColor }
    }
    return iterator.textAttributes?.foregroundColor ?: keyColor ?: defaultForeground
  }

  private fun colorFromMergedAttributes(offset: Int): Color? {
    val editorEx = editorEx ?: return null
    val textLength = document.textLength
    if (textLength <= 0) return defaultForeground

    val boundedOffset = offset.coerceIn(0, textLength)
    val normalizedOffset = if (boundedOffset == textLength) boundedOffset - 1 else boundedOffset
    val line = document.getLineNumber(normalizedOffset)
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    if (lineEnd <= lineStart) return defaultForeground

    val probeOffset = boundedOffset.coerceIn(lineStart, lineEnd - 1)
    val runs = lineColorRunsCache.getOrPut(line) {
      computeLineColorRuns(editorEx, lineStart, lineEnd)
    }

    for (run in runs) {
      if (probeOffset in run.startOffset until run.endOffset) {
        return run.color
      }
    }

    return defaultForeground
  }

  private fun computeLineColorRuns(editorEx: EditorEx, lineStart: Int, lineEnd: Int): List<ColorRun> {
    val runs = ArrayList<ColorRun>()
    val iterationState = IterationState(editorEx, lineStart, lineEnd, null, false, true, false, false)
    while (!iterationState.atEnd()) {
      val startOffset = iterationState.startOffset
      val endOffset = iterationState.endOffset
      if (endOffset > startOffset) {
        val color = iterationState.mergedAttributes.foregroundColor ?: defaultForeground
        runs.add(ColorRun(startOffset, endOffset, color))
      }
      iterationState.advance()
    }

    if (runs.isEmpty()) {
      runs.add(ColorRun(lineStart, lineEnd, defaultForeground))
    }

    return runs
  }

  private fun offsetFromRect(entry: MinimapRenderEntry, metrics: MinimapLayoutMetrics): Int? {
    if (metrics.pxPerColumn <= 0.0 || metrics.baseLineHeight <= 0.0) return null

    val line = ((entry.rect2d.y + areaStart) / metrics.baseLineHeight).toInt().coerceIn(0, metrics.lineCount - 1)
    val column = ((entry.rect2d.x - metrics.contentStartX) / metrics.pxPerColumn).toInt().coerceAtLeast(0)
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    return (lineStart + column).coerceIn(lineStart, lineEnd)
  }

  private fun softenColor(color: Color, background: Color): Color {
    val brightness = (0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue) / 255.0
    val balance = if (brightness < 0.2) 0.45 else 0.25
    return ColorUtil.mix(color, background, balance)
  }
}
