// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.util.PsiNavigateUtil
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import javax.swing.Icon
import javax.swing.UIManager

class PresentationFactory(val editor: EditorImpl) {

  fun text(text: String): InlayPresentation {
    val plainFont = getFont()
    val width = editor.contentComponent.getFontMetrics(plainFont).stringWidth(text)
    val ascent = editor.ascent
    val descent = editor.descent
    val height = ascent - descent
    val textWithoutBox = EffectInlayPresentation(
      TextInlayPresentation(width, height, text, height) {
        plainFont
      },
      plainFont, editor.lineHeight, ascent, descent
    )
    return AttributesTransformerPresentation(textWithoutBox) {
      it.withDefault(attributesOf(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT))
    }
  }

  fun roundWithBackground(base: InlayPresentation): InlayPresentation {
    return BackgroundInlayPresentation(
      InsetPresentation(
        base,
        left = 7,
        right = 7,
        top = 2,
        down = 2
      )
    )
  }

  fun singleText(text: String) : InlayPresentation {
    return roundWithBackground(text(text))
  }

  fun icon(icon: Icon) : IconPresentation = IconPresentation(icon, editor.component)

  fun roundedText(text: String): InlayPresentation {
    return rounding(8, 8, text(text))
  }

  fun hyperlink(base: InlayPresentation): InlayPresentation {
    val dynamic = DynamicPresentation(base)
    // TODO only with ctrl
    return onHover(dynamic) { event ->
      if (event != null) {
        dynamic.delegate = AttributesTransformerPresentation(base) {
          it.with(attributesOf(EditorColors.REFERENCE_HYPERLINK_COLOR))
        }
      } else {
        dynamic.delegate = base
      }
    }
  }

  private fun attributesOf(key: TextAttributesKey?) = editor.colorsScheme.getAttributes(key) ?: TextAttributes()

  fun onHover(base: InlayPresentation, onHover: (MouseEvent?) -> Unit) : InlayPresentation {
    return OnHoverPresentation(base, onHover)
  }

  fun onClick(base: InlayPresentation, onClick: (MouseEvent, Point) -> Unit) : InlayPresentation {
    return OnClickPresentation(base, onClick)
  }


  // TODO ctrl + cmd handling (+ middle click)
  fun navigateSingle(base: InlayPresentation, resolve: () -> PsiElement?): InlayPresentation {
    return onClick(hyperlink(base)) { _, _ ->
      val target = resolve()
      if(target != null) {
        if (target is Navigatable) {
            CommandProcessor.getInstance().executeCommand(target.project, { target.navigate(true) }, null, null)
        }
      }
    }
  }

  fun seq(vararg presentations: InlayPresentation) : InlayPresentation {
    return when (presentations.size) {
      0 -> SpacePresentation(0, 0)
      1 -> presentations.first()
      else -> SequencePresentation(presentations.toList())
    }
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

private fun TextAttributes.withDefault(other: TextAttributes): TextAttributes {
  val result = this.clone()
  if (result.foregroundColor == null) {
    result.foregroundColor = other.foregroundColor
  }
  if (result.backgroundColor == null) {
    result.backgroundColor = other.backgroundColor
  }
  if (result.effectType == null) {
    result.effectType = other.effectType
  }
  if (result.effectColor == null) {
    result.effectColor = other.effectColor
  }
  return result
}