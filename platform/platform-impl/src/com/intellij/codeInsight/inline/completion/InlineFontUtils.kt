// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font

@ApiStatus.Experimental
object InlineFontUtils {
  fun font(editor: Editor): Font {
    return editor.colorsScheme.getFont(EditorFontType.ITALIC)
  }

  val color: Color
    get() {
      return JBColor.GRAY
    }
}
