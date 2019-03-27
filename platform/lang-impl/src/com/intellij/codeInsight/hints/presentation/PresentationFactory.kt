// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import javax.swing.UIManager

class PresentationFactory(val editor: EditorImpl) {

  fun text(text: String): InlayPresentation {
    val plainFont = getFont()
    val width = editor.contentComponent.getFontMetrics(plainFont).stringWidth(text)
    val ascent = editor.ascent
    val descent = editor.descent
    val height = ascent - descent
    return AttributesTransformerPresentation(
      BackgroundInlayPresentation(
        InsetPresentation(
          EffectInlayPresentation(
            TextInlayPresentation(width, height, text, height) {
              plainFont
            },
            plainFont, editor.lineHeight, ascent, descent
          ),
          left = 7,
          right = 7,
          top = 2,
          down = 2
        )
      )
    ) { it.with(editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)) }
  }

  fun roundedText(text: String): InlayPresentation {
    return rounding(8, 8, text(text))
  }

  private fun getFont(): Font {
    val familyName = UIManager.getFont("Label.font").family
    val size = Math.max(1, editor.colorsScheme.editorFontSize - 1)
    val context = getCurrentContext(editor)
    val font = UIUtil.getFontWithFallback(familyName, Font.PLAIN, size)
    val metrics = FontInfo.getFontMetrics(font, context)
    return metrics.font
  }

  private fun getCurrentContext(editor: Editor): FontRenderContext {
    val editorContext = FontInfo.getFontRenderContext(editor.contentComponent)
    return FontRenderContext(editorContext.transform,
                             AntialiasingType.getKeyForCurrentScope(false),
                             if (editor is EditorImpl)
                               editor.myFractionalMetricsHintValue
                             else
                               RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
  }

  fun rounding(arcWidth: Int, arcHeight: Int, presentation: InlayPresentation): InlayPresentation =
    RoundPresentation(presentation, arcWidth, arcHeight)
}

private fun TextAttributes.with(other: TextAttributes): TextAttributes {
  val result = this.clone()
  other.foregroundColor?.let { result.foregroundColor = it }
  other.backgroundColor?.let { result.backgroundColor = it }
  other.fontType.let { result.fontType = it }
  other.effectType?.let { result.effectType = it }
  other.effectColor?.let { result.effectColor = it }
  return result
}