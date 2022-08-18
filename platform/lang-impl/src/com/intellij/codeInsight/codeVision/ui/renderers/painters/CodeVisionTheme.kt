package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.awt.Rectangle

class CodeVisionTheme(
  var iconGap: Int = JBUI.scale(2),
  var left: Int = 0,
  var right: Int = 0,
  var top: Int = 0,
  var bottom: Int = 0
) {
  companion object {
    fun editorFont(editor: Editor, style: EditorFontType = EditorFontType.PLAIN): Font = editor.colorsScheme.getFont(style)

    fun yInInlayBounds(y: Int, size: Rectangle): Boolean {
      return y >= size.y && y <= (size.y + size.height - (size.height / 4))
    }
  }
}