package com.intellij.codeInsight.grayText

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font

@ApiStatus.Internal
object InlineFontUtils {
  fun font(editor: Editor): Font {
    return editor.colorsScheme.getFont(EditorFontType.ITALIC)
  }

  val color: Color
    get() {
      return JBColor.GRAY
    }
}
