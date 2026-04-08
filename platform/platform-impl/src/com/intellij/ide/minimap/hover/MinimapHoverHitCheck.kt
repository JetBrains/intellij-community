// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.geometry.MinimapLineGeometryUtil
import com.intellij.ide.minimap.layout.MinimapLayoutUtil.lineTop
import com.intellij.ide.minimap.render.MinimapRenderEntry
import com.intellij.ide.minimap.model.MinimapStructureMarkerPolicy
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import kotlin.math.ceil

class MinimapHoverHitCheck(private val editor: Editor) {
  private val structureMarkerPolicy = MinimapStructureMarkerPolicy.forEditor(editor)

  private data class HoverData(
    val range: TextRange,
    val text: String?,
    val icon: Icon?
  )

  fun hitCheck(snapshot: MinimapSnapshot, point: Point?): MinimapHoverHitCheckResult? {
    if (point == null) return null
    val entries = snapshot.structureEntries
    if (entries.isEmpty()) return null

    val context = snapshot.context
    var bestResult: MinimapHoverHitCheckResult? = null
    var bestArea = Long.MAX_VALUE

    for (entry in entries) {
      val data = resolveHoverData(entry) ?: continue
      val rect = computeHoverRect(data.range, context) ?: continue
      if (!rect.contains(point)) continue

      val area = rect.width.toLong() * rect.height.toLong()

      if (area < bestArea) {
        bestArea = area
        bestResult = MinimapHoverHitCheckResult(entry, rect, data.text, data.icon)
      }
    }

    return bestResult
  }

  fun computeHoverRect(entry: MinimapRenderEntry, context: MinimapRenderContext): Rectangle? {
    val range = resolveRange(entry) ?: return null
    return computeHoverRect(range, context)
  }

  private fun resolveRange(entry: MinimapRenderEntry): TextRange? {
    val element = entry.element ?: return null

    return ReadAction.computeBlocking<TextRange?, RuntimeException> {
      val value = element.value ?: return@computeBlocking null
      if (!structureMarkerPolicy.isRelevantStructureElement(element, value)) return@computeBlocking null

      when (value) {
        is PsiElement -> value.textRange
        is TextRange -> value
        else -> null
      }
    }
  }

  private fun computeHoverRect(range: TextRange, context: MinimapRenderContext): Rectangle? {
    val document = editor.document
    val lineProjection = context.lineProjection
    val lineCount = lineProjection.projectedLineCount
    val geometry = context.geometry

    if (lineCount <= 0 || geometry.minimapHeight <= 0 || context.panelWidth <= 0) return null

    val startLine = document.getLineNumber(range.startOffset.coerceAtLeast(0))
    val projectedStartLine = lineProjection.logicalToProjectedLine(startLine) ?: return null

    val endLine = document.getLineNumber(range.endOffset.coerceAtLeast(range.startOffset))
    val projectedEndLine = lineProjection.logicalToProjectedLine(endLine) ?: projectedStartLine
    val endLineExclusive = (projectedEndLine + 1)
      .coerceAtMost(lineCount)
      .coerceAtLeast(projectedStartLine + 1)

    val baseLineHeight = MinimapLineGeometryUtil.baseLineHeight(lineCount, geometry.minimapHeight)
    val lineGap = MinimapLineGeometryUtil.lineGap(baseLineHeight)

    val y1 = lineTop(projectedStartLine, baseLineHeight)
    val y2 = lineTop(endLineExclusive, baseLineHeight)

    val heightPx = ceil(y2 - y1 - lineGap).toInt().coerceAtLeast(1)
    val y = (y1 - geometry.areaStart + lineGap / 2.0).toInt()

    val width = context.panelWidth.coerceAtLeast(1)
    return Rectangle(0, y, width, heightPx)
  }

  private fun resolveHoverData(entry: MinimapRenderEntry): HoverData? {
    val element = entry.element ?: return null

    return ReadAction.computeBlocking<HoverData?, RuntimeException> {
      val value = element.value ?: return@computeBlocking null
      if (!structureMarkerPolicy.isRelevantStructureElement(element, value)) return@computeBlocking null

      val range = when (value) {
        is PsiElement -> value.textRange
        is TextRange -> value
        else -> null
      } ?: return@computeBlocking null

      val presentation = element.presentation
      val text = getText(presentation, value)
      val icon = if (text != null) presentation.getIcon(false) else null

      HoverData(range, text, icon)
    }
  }

  private fun getText(presentation: ItemPresentation, value: Any): String? {
    val presentableText = presentation.presentableText?.takeUnless { it.isBlank() }
    if (presentableText != null) return presentableText

    val owner = value as? PsiNameIdentifierOwner ?: return null
    return owner.name?.takeUnless { it.isBlank() }
  }

}
