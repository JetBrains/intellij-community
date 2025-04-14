// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INLINE_SUGGESTION
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.JdkConstants
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font

object InlineCompletionFontUtils {
  fun font(editor: Editor): Font {
    return editor.colorsScheme.getFont(EditorFontType.ITALIC)
  }

  fun color(editor: Editor): Color {
    return attributes(editor).foregroundColor
  }

  @ApiStatus.Experimental
  fun attributes(editor: Editor): TextAttributes {
    return editor.colorsScheme.getAttributes(INLINE_SUGGESTION)
  }

  internal fun getFont(editor: Editor, text: String, @JdkConstants.FontStyle fontType: Int): Font {
    val original = editor.colorsScheme.getFont(EditorFontType.forJavaStyle(fontType))
    return UIUtil.getFontWithFallbackIfNeeded(original, text)
  }
}
