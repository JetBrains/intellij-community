package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

class CodeVisionTheme(
  var iconGap: Int = JBUI.scale(2),
  var left: Int = 0,
  var right: Int = 0,
  var top: Int = 0,
  var bottom: Int = 0
) {
  companion object {
    //todo rider proper color keys
    fun foregroundColor(editor: Editor, hovered: Boolean): Color {
      return if (hovered) {
        JBUI.CurrentTheme.Link.Foreground.ENABLED
      }
      else {
        JBColor.GRAY
      }
    }

    fun font(editor: Editor, style: Int = Font.PLAIN): Font {
      val size = lensFontSize(editor)
      return UIUtil.getLabelFont().deriveFont(style, size)
    }

    fun lensFontSize(editor: Editor) =
      min(max(editor.colorsScheme.editorFontSize * 80f / 100, 10f), editor.colorsScheme.editorFontSize.toFloat())

    fun editorFont(editor: Editor, style: EditorFontType = EditorFontType.PLAIN): Font = editor.colorsScheme.getFont(style)

    fun yInInlayBounds(y: Int, size: Rectangle): Boolean {
      return y >= size.y && y <= (size.y + size.height - (size.height / 4))
    }
  }
}