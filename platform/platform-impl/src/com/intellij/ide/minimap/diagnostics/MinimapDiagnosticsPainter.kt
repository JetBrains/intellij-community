// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.diagnostics

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

class MinimapDiagnosticsPainter(private val editor: Editor) {
  fun paint(graphics: Graphics2D, entries: List<MinimapDiagnosticEntry>) {
    if (entries.isEmpty()) return

    val warningStyle = styleFor(MinimapDiagnosticSeverity.WARNING)
    val errorStyle = styleFor(MinimapDiagnosticSeverity.ERROR)

    val oldComposite = graphics.composite
    val oldStroke = graphics.stroke
    try {
      for (entry in entries) {
        val style = when (entry.severity) {
          MinimapDiagnosticSeverity.WARNING -> warningStyle
          MinimapDiagnosticSeverity.ERROR -> errorStyle
        }
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, style.alpha)
        graphics.stroke = style.stroke
        graphics.color = style.color
        graphics.draw(expanded(entry.rect2d, style.padding))
      }
    }
    finally {
      graphics.stroke = oldStroke
      graphics.composite = oldComposite
    }
  }

  private fun styleFor(severity: MinimapDiagnosticSeverity): MinimapDiagnosticRenderStyle {
    val scheme = editor.colorsScheme
    return when (severity) {
      MinimapDiagnosticSeverity.WARNING -> MinimapDiagnosticRenderStyle(
        color = scheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES)?.errorStripeColor
                ?: scheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES)?.effectColor
                ?: scheme.defaultForeground,
        alpha = WARNING_ALPHA,
        stroke = BasicStroke(WARNING_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
        padding = WARNING_PADDING,
      )

      MinimapDiagnosticSeverity.ERROR -> MinimapDiagnosticRenderStyle(
        color = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)?.errorStripeColor
                ?: scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)?.effectColor
                ?: scheme.defaultForeground,
        alpha = ERROR_ALPHA,
        stroke = BasicStroke(ERROR_STROKE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND),
        padding = ERROR_PADDING,
      )
    }
  }

  private fun expanded(source: Rectangle2D.Double, padding: Double): Rectangle2D.Double {
    return Rectangle2D.Double(
      source.x - padding,
      source.y - padding,
      source.width + 2 * padding,
      source.height + 2 * padding,
    )
  }


  companion object {
    private const val WARNING_ALPHA: Float = 0.9f
    private const val ERROR_ALPHA: Float = 0.95f

    private const val WARNING_STROKE_WIDTH: Float = 1.2f
    private const val ERROR_STROKE_WIDTH: Float = 1.5f

    private const val WARNING_PADDING: Double = 0.5
    private const val ERROR_PADDING: Double = 0.7
  }
}
