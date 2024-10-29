// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.EffectPainter2D
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point

@ApiStatus.Internal
open class CodeVisionVisionTextPainter<T>(
  val printer: (T) -> String = { it.toString() },
  theme: CodeVisionTheme? = null
) : ICodeVisionEntryBasePainter<T> {
  private val theme: CodeVisionTheme = theme ?: CodeVisionTheme()

  override fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics,
    value: T,
    point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean,
    hoveredEntry: CodeVisionEntry?
  ) {
    g as Graphics2D
    val themeInfoProvider = service<CodeVisionThemeInfoProvider>()

    val inSelectedBlock = textAttributes.backgroundColor == editor.selectionModel.textAttributes.backgroundColor
    g.color = if (inSelectedBlock) editor.selectionModel.textAttributes.foregroundColor ?: editor.colorsScheme.defaultForeground
    else themeInfoProvider.foregroundColor(editor, hovered)

    g.font = themeInfoProvider.font(editor)
    val x = point.x + theme.left
    var y = point.y + theme.top
    g.drawString(printer.invoke(value), x, y)
    if (hovered) {
      val size = size(editor, state, value)
      y += JBUI.scale(1)

      EffectPainter2D.LINE_UNDERSCORE.paint(g, x.toDouble(), y.toDouble(), size.width.toDouble(), 5.0, g.font)
    }
  }

  override fun size(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: T
  ): Dimension {
    val fontMetrics = editor.component.getFontMetrics(service<CodeVisionThemeInfoProvider>().font(editor))
    return Dimension(
      fontMetrics.stringWidth(printer.invoke(value)) + theme.left + theme.right,
      fontMetrics.height + theme.top + theme.bottom
    )
  }
}

