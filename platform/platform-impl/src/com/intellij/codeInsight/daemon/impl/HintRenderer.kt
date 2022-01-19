// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.HintWidthAdjustment
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FocusModeModel
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.ui.paint.EffectPainter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.StartupUiUtil
import org.intellij.lang.annotations.JdkConstants
import java.awt.*
import java.awt.font.FontRenderContext
import javax.swing.UIManager
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

open class HintRenderer(var text: String?) : EditorCustomElementRenderer {
  var widthAdjustment: HintWidthAdjustment? = null

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return calcWidthInPixels(inlay.editor, text, widthAdjustment, useEditorFont())
  }

  protected open fun getTextAttributes(editor: Editor): TextAttributes? {
    return editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    val editor = inlay.editor
    if (editor !is EditorImpl) return

    val focusModeRange = editor.focusModeRange
    val attributes = if (focusModeRange != null && (inlay.offset <= focusModeRange.startOffset || focusModeRange.endOffset <= inlay.offset)) {
      editor.getUserData(FocusModeModel.FOCUS_MODE_ATTRIBUTES) ?: getTextAttributes(editor)
    }
    else {
      getTextAttributes(editor)
    }

    paintHint(g, editor, r, text, attributes, attributes ?: textAttributes, widthAdjustment, useEditorFont())
  }

  /**
   * @deprecated
   * @see calcHintTextWidth
   */
  protected fun doCalcWidth(text: String?, fontMetrics: FontMetrics): Int {
    return calcHintTextWidth(text, fontMetrics)
  }

  protected open fun useEditorFont() = useEditorFontFromSettings()

  companion object {
    @JvmStatic
    fun calcWidthInPixels(editor: Editor, text: String?, widthAdjustment: HintWidthAdjustment?): Int {
      return calcWidthInPixels(editor, text, widthAdjustment, useEditorFontFromSettings())
    }

    @JvmStatic
    fun calcWidthInPixels(editor: Editor, text: String?, widthAdjustment: HintWidthAdjustment?, useEditorFont: Boolean): Int {
      val fontMetrics = getFontMetrics(editor, useEditorFont).metrics
      return calcHintTextWidth(text, fontMetrics) + calcWidthAdjustment(text, editor, fontMetrics, widthAdjustment)
    }

    @JvmStatic
    fun paintHint(g: Graphics,
                  editor: EditorImpl,
                  r: Rectangle,
                  text: String?,
                  attributes: TextAttributes?,
                  textAttributes: TextAttributes,
                  widthAdjustment: HintWidthAdjustment?) {
      paintHint(g, editor, r, text, attributes, textAttributes, widthAdjustment, useEditorFontFromSettings())
    }

    @JvmStatic
    fun paintHint(g: Graphics,
                  editor: EditorImpl,
                  r: Rectangle,
                  text: String?,
                  attributes: TextAttributes?,
                  textAttributes: TextAttributes,
                  widthAdjustment: HintWidthAdjustment?,
                  useEditorFont: Boolean) {
      val ascent = editor.ascent
      val descent = editor.descent
      val g2d = g as Graphics2D

      if (text != null && attributes != null) {
        val fontMetrics = getFontMetrics(editor, useEditorFont)
        val gap = if (r.height < fontMetrics.lineHeight + 2) 1 else 2
        val backgroundColor = attributes.backgroundColor
        if (backgroundColor != null) {
          val alpha = if (isInsufficientContrast(attributes, textAttributes)) 1.0f else BACKGROUND_ALPHA
          val config = GraphicsUtil.setupAAPainting(g)
          GraphicsUtil.paintWithAlpha(g, alpha)
          g.setColor(backgroundColor)
          g.fillRoundRect(r.x + 2, r.y + gap, r.width - 4, r.height - gap * 2, 8, 8)
          config.restore()
        }
        val foregroundColor = attributes.foregroundColor
        if (foregroundColor != null) {
          val savedHint = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
          val savedClip = g.getClip()

          g.setColor(foregroundColor)
          g.setFont(getFont(editor, useEditorFont))
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
          g.clipRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4)
          val metrics = fontMetrics.metrics
          val startX = r.x + 7
          val startY = r.y + max(ascent, (r.height + metrics.ascent - metrics.descent) / 2) - 1

          val adjustment = calcWidthAdjustment(text, editor, g.fontMetrics, widthAdjustment)
          if (adjustment == 0) {
            g.drawString(text, startX, startY)
          }
          else {
            val adjustmentPosition = widthAdjustment!!.adjustmentPosition
            val firstPart = text.substring(0, adjustmentPosition)
            val secondPart = text.substring(adjustmentPosition)
            g.drawString(firstPart, startX, startY)
            g.drawString(secondPart, startX + g.getFontMetrics().stringWidth(firstPart) + adjustment, startY)
          }

          g.setClip(savedClip)
          g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
        }
      }
      val effectColor = textAttributes.effectColor
      val effectType = textAttributes.effectType
      if (effectColor != null) {
        g.setColor(effectColor)
        val xStart = r.x
        val xEnd = r.x + r.width
        val y = r.y + ascent
        val font = editor.getColorsScheme().getFont(EditorFontType.PLAIN)
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (effectType) {
          EffectType.LINE_UNDERSCORE -> EffectPainter.LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
          EffectType.BOLD_LINE_UNDERSCORE -> EffectPainter.BOLD_LINE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
          EffectType.STRIKEOUT -> EffectPainter.STRIKE_THROUGH.paint(g2d, xStart, y, xEnd - xStart, editor.charHeight, font)
          EffectType.WAVE_UNDERSCORE -> EffectPainter.WAVE_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
          EffectType.BOLD_DOTTED_LINE -> EffectPainter.BOLD_DOTTED_UNDERSCORE.paint(g2d, xStart, y, xEnd - xStart, descent, font)
        }
      }
    }

    private fun isInsufficientContrast(
      attributes: TextAttributes,
      surroundingAttributes: TextAttributes
    ): Boolean {
      val backgroundUnderHint = surroundingAttributes.backgroundColor
      if (backgroundUnderHint != null && attributes.foregroundColor != null) {
        val backgroundBlended = srcOverBlend(attributes.backgroundColor, backgroundUnderHint, BACKGROUND_ALPHA)

        val backgroundBlendedGrayed = backgroundBlended.toGray()
        val textGrayed = attributes.foregroundColor.toGray()
        val delta = Math.abs(backgroundBlendedGrayed - textGrayed)
        return delta < 10
      }
      return false
    }

    private fun Color.toGray(): Double {
      return (0.30 * red) + (0.59 * green) + (0.11 * blue)
    }

    private fun srcOverBlend(foreground: Color, background: Color, foregroundAlpha: Float): Color {
      val r = foreground.red * foregroundAlpha + background.red * (1.0f - foregroundAlpha)
      val g = foreground.green * foregroundAlpha + background.green * (1.0f - foregroundAlpha)
      val b = foreground.blue * foregroundAlpha + background.blue * (1.0f - foregroundAlpha)
      return Color(r.roundToInt(), g.roundToInt(), b.roundToInt())
    }

    private fun calcWidthAdjustment(text: String?, editor: Editor, fontMetrics: FontMetrics, widthAdjustment: HintWidthAdjustment?): Int {
      if (widthAdjustment == null || editor !is EditorImpl) return 0
      val editorTextWidth = editor.getFontMetrics(Font.PLAIN).stringWidth(widthAdjustment.editorTextToMatch)
      return max(0, editorTextWidth
                         + calcHintTextWidth(widthAdjustment.hintTextToMatch, fontMetrics)
                         - calcHintTextWidth(text, fontMetrics))
    }

    class MyFontMetrics internal constructor(editor: Editor, size: Int, @JdkConstants.FontStyle fontType: Int, useEditorFont: Boolean) {
      val metrics: FontMetrics
      val lineHeight: Int

      val font: Font
        get() = metrics.font

      init {
        val font = if (useEditorFont) {
          val editorFont = EditorUtil.getEditorFont()
          editorFont.deriveFont(fontType, size.toFloat())
        } else {
          val familyName = UIManager.getFont("Label.font").family
          StartupUiUtil.getFontWithFallback(familyName, fontType, size)
        }
        val context = getCurrentContext(editor)
        metrics = FontInfo.getFontMetrics(font, context)
        // We assume this will be a better approximation to a real line height for a given font
        lineHeight = ceil(font.createGlyphVector(context, "Ap").visualBounds.height).toInt()
      }

      fun isActual(editor: Editor, size: Int, fontType: Int, familyName: String): Boolean {
        val font = metrics.font
        if (familyName != font.family || size != font.size || fontType != font.style) return false
        val currentContext = getCurrentContext(editor)
        return currentContext.equals(metrics.fontRenderContext)
      }

      private fun getCurrentContext(editor: Editor): FontRenderContext {
        val editorContext = FontInfo.getFontRenderContext(editor.contentComponent)
        return FontRenderContext(editorContext.transform,
                                 AntialiasingType.getKeyForCurrentScope(false),
                                 UISettings.editorFractionalMetricsHint)
      }
    }

    @JvmStatic
    protected fun getFontMetrics(editor: Editor, useEditorFont: Boolean): MyFontMetrics {
      val size = max(1, editor.colorsScheme.editorFontSize - 1)
      var metrics = editor.getUserData(HINT_FONT_METRICS)
      val attributes = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
      val fontType = attributes.fontType
      val familyName = if (useEditorFont) {
        EditorColorsManager.getInstance().globalScheme.editorFontName
      }
      else {
        StartupUiUtil.getLabelFont().family
      }
      if (metrics != null && !metrics.isActual(editor, size, fontType, familyName)) {
        metrics = null
      }
      if (metrics == null) {
        metrics = MyFontMetrics(editor, size, fontType, useEditorFont)
        editor.putUserData(HINT_FONT_METRICS, metrics)
      }
      return metrics
    }

    @JvmStatic
    fun useEditorFontFromSettings() = EditorSettingsExternalizable.getInstance().isUseEditorFontInInlays

    private fun getFont(editor: Editor, useEditorFont: Boolean): Font {
      return getFontMetrics(editor, useEditorFont).font
    }

    @JvmStatic
    protected fun calcHintTextWidth(text: String?, fontMetrics: FontMetrics): Int {
      return if (text == null) 0 else fontMetrics.stringWidth(text) + 14
    }

    private val HINT_FONT_METRICS = Key.create<MyFontMetrics>("ParameterHintFontMetrics")
    private const val BACKGROUND_ALPHA = 0.55f
  }

  // workaround for KT-12063 "IllegalAccessError when accessing @JvmStatic protected member of a companion object from a subclass"
  @JvmSynthetic
  @JvmName("getFontMetrics$")
  protected fun getFontMetrics(editor: Editor, useEditorFont: Boolean): MyFontMetrics = Companion.getFontMetrics(editor, useEditorFont)
}
