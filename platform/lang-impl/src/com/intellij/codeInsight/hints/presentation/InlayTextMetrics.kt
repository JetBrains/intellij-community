// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.getFontWithFallback
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.FontRenderContext
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.max

/** @see com.intellij.codeInsight.hints.InlayHintsUtils.getTextMetricStorage */
@ApiStatus.Internal
class InlayTextMetricsStorage(val editor: Editor) {
  private var smallTextMetrics : InlayTextMetrics? = null
  private var normalTextMetrics : InlayTextMetrics? = null

  private var lastStamp : InlayTextMetricsStamp? = null

  private val smallTextSize: Float
    @RequiresEdt
    get() = max(1f, normalTextSize - 1f)

  private val normalTextSize: Float
    @RequiresEdt
    get() = editor.colorsScheme.editorFontSize2D

  @RequiresEdt
  fun getCurrentStamp(): InlayTextMetricsStamp {
    val lastStamp = lastStamp
    if (lastStamp == null
        || normalTextSize != lastStamp.editorFontSize2D
        || UISettings.getInstance().ideScale != lastStamp.ideScale
        || getFontFamilyName() != lastStamp.familyName
        || getFontRenderContext(editor.component) != lastStamp.fontRenderContext) {
      return doGetCurrentStamp()
    }
    return lastStamp
  }

  @RequiresEdt
  private fun doGetCurrentStamp(): InlayTextMetricsStamp {
    return InlayTextMetricsStamp(
      // smallTextSize is derived from normalTextSize, so it can serve as a stamp for both metrics
      normalTextSize,
      getFontFamilyName(),
      UISettings.getInstance().ideScale,
      getFontRenderContext(editor.component)
    )
  }

  @RequiresEdt
  fun getFontMetrics(small: Boolean): InlayTextMetrics {
    val currentStamp = getCurrentStamp()
    // a new stamp is only ever constructed if a change is detected
    if (lastStamp !== currentStamp) {
      lastStamp = currentStamp
      smallTextMetrics = null
      normalTextMetrics = null
    }

    var metrics: InlayTextMetrics?
    if (small) {
      metrics = smallTextMetrics
      if (metrics == null) {
        metrics = InlayTextMetrics.create(editor, smallTextSize, getFontType(), currentStamp.fontRenderContext)
        smallTextMetrics = metrics
      }
    }
    else {
      metrics = normalTextMetrics
      if (metrics == null) {
        metrics = InlayTextMetrics.create(editor, normalTextSize, getFontType(), currentStamp.fontRenderContext)
        normalTextMetrics = metrics
      }
    }
    return metrics
  }

  private fun getFontFamilyName() : String {
    return if (EditorSettingsExternalizable.getInstance().isUseEditorFontInInlays) {
      EditorColorsManager.getInstance().globalScheme.editorFontName
    } else {
      StartupUiUtil.labelFont.family
    }
  }

  private fun getFontType(): Int {
    return editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLAY_DEFAULT).fontType
  }

}

@ApiStatus.Internal
class InlayTextMetricsStamp internal constructor(
  val editorFontSize2D: Float,
  val familyName: String,
  val ideScale: Float,
  val fontRenderContext: FontRenderContext
)

class InlayTextMetrics(
  editor: Editor,
  val fontHeight: Int,
  val fontBaseline: Int,
  private val fontMetrics: FontMetrics,
  val fontType: Int,
  private val ideScale: Float,
) {
  companion object {
    internal fun create(editor: Editor, size: Float, fontType: Int, context: FontRenderContext) : InlayTextMetrics {
      val font = if (EditorSettingsExternalizable.getInstance().isUseEditorFontInInlays) {
        val editorFont = EditorUtil.getEditorFont()
        editorFont.deriveFont(fontType, size)
      } else {
        val familyName = StartupUiUtil.labelFont.family
        getFontWithFallback(familyName, fontType, size)
      }
      val metrics = FontInfo.getFontMetrics(font, context)
      // We assume this will be a better approximation to a real line height for a given font
      val fontHeight = ceil(font.createGlyphVector(context, "Albpq@").visualBounds.height).toInt()
      val fontBaseline = ceil(font.createGlyphVector(context, "Alb").visualBounds.height).toInt()
      return InlayTextMetrics(editor, fontHeight, fontBaseline, metrics, fontType, UISettings.getInstance().ideScale)
    }
  }

  val font: Font
    get() = fontMetrics.font

  // Editor metrics:
  val ascent: Int = editor.ascent
  val descent: Int = (editor as? EditorImpl)?.descent ?: 0
  val lineHeight: Int = editor.lineHeight
  private val editorComponent = editor.component

  @Deprecated("Use InlayTextMetricsStorage.getCurrentStamp() to ensure actual metrics are used")
  fun isActual(size: Float, familyName: String) : Boolean {
    if (size != font.size2D) return false
    if (font.family != familyName) return false
    if (ideScale != UISettings.getInstance().ideScale) return false
    return getFontRenderContext(editorComponent).equals(fontMetrics.fontRenderContext)
  }

  /**
   * Offset from the top edge of drawing rectangle to rectangle with text.
   */
  fun offsetFromTop(): Int = (lineHeight - fontHeight) / 2

  fun getStringWidth(text: String): Int {
    return fontMetrics.stringWidth(text)
  }
}

private fun getFontRenderContext(editorComponent: JComponent): FontRenderContext {
  val editorContext = FontInfo.getFontRenderContext(editorComponent)
  return FontRenderContext(editorContext.transform,
                           AntialiasingType.getKeyForCurrentScope(false),
                           UISettings.editorFractionalMetricsHint)
}