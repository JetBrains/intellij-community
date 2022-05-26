// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.FontRenderContext
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.max

@ApiStatus.Internal
class InlayTextMetricsStorage(val editor: EditorImpl) {
  private var smallTextMetrics : InlayTextMetrics? = null
  private var normalTextMetrics : InlayTextMetrics? = null

  val smallTextSize: Int
    @RequiresEdt
    get() = max(1, editor.colorsScheme.editorFontSize - 1)


  val normalTextSize: Int
    @RequiresEdt
    get() = editor.colorsScheme.editorFontSize

  @RequiresEdt
  fun getFontMetrics(small: Boolean): InlayTextMetrics {
    var metrics: InlayTextMetrics?

    val familyName = if (EditorSettingsExternalizable.getInstance().isUseEditorFontInInlays) {
      EditorColorsManager.getInstance().globalScheme.editorFontName
    } else {
      StartupUiUtil.getLabelFont().family
    }
    val fontType = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLAY_DEFAULT).fontType

    if (small) {
      metrics = smallTextMetrics
      val fontSize = smallTextSize
      if (metrics == null || !metrics.isActual(smallTextSize, familyName)) {
        metrics = InlayTextMetrics.create(editor, fontSize, fontType)
        smallTextMetrics = metrics
      }
    } else {
      metrics = normalTextMetrics
      val fontSize = normalTextSize
      if (metrics == null || !metrics.isActual(normalTextSize, familyName)) {
        metrics = InlayTextMetrics.create(editor, fontSize, fontType)
        normalTextMetrics = metrics
      }
    }
    return metrics
  }
}

class InlayTextMetrics(
  editor: EditorImpl,
  val fontHeight: Int,
  val fontBaseline: Int,
  private val fontMetrics: FontMetrics,
  val fontType: Int
) {
  companion object {
    internal fun create(editor: EditorImpl, size: Int, fontType: Int) : InlayTextMetrics {
      val font = if (EditorSettingsExternalizable.getInstance().isUseEditorFontInInlays) {
        val editorFont = EditorUtil.getEditorFont()
        editorFont.deriveFont(fontType, size.toFloat())
      } else {
        val familyName = StartupUiUtil.getLabelFont().family
        UIUtil.getFontWithFallback(familyName, fontType, size)
      }
      val context = getCurrentContext(editor.component)
      val metrics = FontInfo.getFontMetrics(font, context)
      // We assume this will be a better approximation to a real line height for a given font
      val fontHeight = ceil(font.createGlyphVector(context, "Albpq@").visualBounds.height).toInt()
      val fontBaseline = ceil(font.createGlyphVector(context, "Alb").visualBounds.height).toInt()
      return InlayTextMetrics(editor, fontHeight, fontBaseline, metrics, fontType)
    }

    private fun getCurrentContext(editorComponent: JComponent): FontRenderContext {
      val editorContext = FontInfo.getFontRenderContext(editorComponent)
      return FontRenderContext(editorContext.transform,
                               AntialiasingType.getKeyForCurrentScope(false),
                               UISettings.editorFractionalMetricsHint)
    }
  }

  val font: Font
    get() = fontMetrics.font

  // Editor metrics:
  val ascent: Int = editor.ascent
  val descent: Int = editor.descent
  private val lineHeight = editor.lineHeight
  private val editorComponent = editor.component

  fun isActual(size: Int, familyName: String) : Boolean {
    if (size != font.size) return false
    if (font.family != familyName) return false
    return getCurrentContext(editorComponent).equals(fontMetrics.fontRenderContext)
  }

  /**
   * Offset from the top edge of drawing rectangle to rectangle with text.
   */
  fun offsetFromTop(): Int = (lineHeight - fontHeight) / 2

  fun getStringWidth(text: String): Int {
    return fontMetrics.stringWidth(text)
  }
}