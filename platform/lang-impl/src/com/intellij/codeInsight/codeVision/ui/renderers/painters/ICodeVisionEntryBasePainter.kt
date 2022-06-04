package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import javax.swing.Icon

interface ICodeVisionEntryBasePainter<T> : ICodeVisionPainter {
  fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics, value: T, point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean
  )

  fun size(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: T
  ): Dimension

  fun toIcon(editor: Editor,
             textAttributes: TextAttributes,
             value: T,
             state: RangeCodeVisionModel.InlayState,
             hovered: Boolean) = object : Icon {
    var size = size(editor, state, value)

    override fun getIconHeight(): Int = size.height

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
      paint(editor, textAttributes, g, value, Point(x, y + (editor as EditorImpl).ascent), state, hovered)
    }

    override fun getIconWidth(): Int = size.width
  }
}