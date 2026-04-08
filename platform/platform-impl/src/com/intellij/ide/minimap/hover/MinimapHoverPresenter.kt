// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.geometry.MinimapLineGeometryUtil
import com.intellij.ide.minimap.render.MinimapRenderContext
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics2D
import kotlin.math.roundToInt

class MinimapHoverPresenter(private val panel: MinimapPanel) {
  private val hoverPainter = MinimapHoverPainter()
  private val balloonController = MinimapBalloonController(panel)
  private var activeTarget: MinimapHoverTarget? = null
  private var lastContext: MinimapRenderContext? = null

  fun setContext(context: MinimapRenderContext?) {
    lastContext = context
  }

  fun setTarget(target: MinimapHoverTarget?) {
    if (target == null && activeTarget == null) return
    if (target != null && target.sameAs(activeTarget)) return
    activeTarget = target
    if (target == null) {
      balloonController.hide()
      return
    }
    MinimapUsageCollector.logHoverShown(
      scaleMode = panel.settings.state.scaleMode,
      targetType = hoverTargetType(target),
    )
    balloonController.show(target.text, target.rect, target.icon)
  }

  fun paint(graphics: Graphics2D) {
    val target = activeTarget ?: return
    val context = lastContext ?: return

    val lineHeight = computeLineHeight(context)
    hoverPainter.paint(graphics, target.rect, lineHeight, hoverColor())
  }

  fun hide() {
    activeTarget = null
    balloonController.hide()
  }

  private fun computeLineHeight(context: MinimapRenderContext): Int {
    val lineCount = panel.editor.document.lineCount
    if (lineCount <= 0) return 1
    val baseLineHeight = MinimapLineGeometryUtil.baseLineHeight(lineCount, context.geometry.minimapHeight)
    val lineGap = MinimapLineGeometryUtil.lineGap(baseLineHeight)

    return MinimapLineGeometryUtil.lineHeight(baseLineHeight, lineGap).roundToInt().coerceAtLeast(1)
  }

  private fun hoverTargetType(target: MinimapHoverTarget): MinimapUsageCollector.HoverTargetType {
    return if (target.entry.element != null) MinimapUsageCollector.HoverTargetType.STRUCTURE
    else MinimapUsageCollector.HoverTargetType.UNKNOWN
  }

  private fun hoverColor(): Color {
    val scheme = panel.editor.colorsScheme
    return scheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)?.foregroundColor
           ?: JBColor.BLUE
  }
}
