package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point

open class CodeVisionStringPainter(val text: String, val theme: CodeVisionTheme? = null) : ICodeVisionGraphicPainter {
  private val painter = CodeVisionVisionTextPainter<String>(theme = theme)

  override fun paint(
    editor: Editor, textAttributes: TextAttributes, g: Graphics,
    point: Point, state: RangeCodeVisionModel.InlayState, hovered: Boolean
  ) {
    painter.paint(editor, textAttributes, g, text, point, state, hovered)
  }

  override fun size(editor: Editor, state: RangeCodeVisionModel.InlayState): Dimension {
    return painter.size(editor, state, text)
  }
}