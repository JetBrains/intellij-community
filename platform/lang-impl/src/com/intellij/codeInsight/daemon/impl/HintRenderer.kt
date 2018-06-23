// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl


import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.ui.paint.EffectPainter
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.font.FontRenderContext
import javax.swing.UIManager

/**
 * @author egor
 */
open class HintRenderer(var text: String?) : EditorCustomElementRenderer {

  override fun calcWidthInPixels(editor: Editor): Int {
    return doCalcWidth(text, getFontMetrics(editor).metrics)
  }

  protected open fun getTextAttributes(editor: Editor): TextAttributes? {
    return editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)
  }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle, textAttributes: TextAttributes) {
    if (editor !is EditorImpl) return
    val ascent = editor.ascent
    val descent = editor.descent
    val g2d = g as Graphics2D
    val attributes = getTextAttributes(editor)
    if (text != null && attributes != null) {
      val fontMetrics = getFontMetrics(editor)
      val gap = if (r.height < fontMetrics.lineHeight + 2) 1 else 2
      val backgroundColor = attributes.backgroundColor
      if (backgroundColor != null) {
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.setColor(backgroundColor)
        g.fillRoundRect(r.x + 2, r.y + gap, r.width - 4, r.height - gap * 2, 8, 8)
        config.restore()
      }
      val foregroundColor = attributes.foregroundColor
      if (foregroundColor != null) {
        val savedHint = g2d.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
        val savedClip = g.getClip()

        g.setColor(foregroundColor)
        g.setFont(getFont(editor))
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
        g.clipRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4)
        val metrics = fontMetrics.metrics
        g.drawString(text, r.x + 7, r.y + Math.max(ascent, (r.height + metrics.ascent - metrics.descent) / 2) - 1)

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

  protected class MyFontMetrics constructor(editor: Editor, familyName: String, size: Int) {
    val metrics: FontMetrics
    val lineHeight: Int

    val font: Font
      get() = metrics.font

    init {
      val font = UIUtil.getFontWithFallback(familyName, Font.PLAIN, size)
      val context = getCurrentContext(editor)
      metrics = FontInfo.getFontMetrics(font, context)
      // We assume this will be a better approximation to a real line height for a given font
      lineHeight = Math.ceil(font.createGlyphVector(context, "Ap").visualBounds.height).toInt()
    }

    fun isActual(editor: Editor, familyName: String, size: Int): Boolean {
      val font = metrics.font
      if (familyName != font.family || size != font.size) return false
      val currentContext = getCurrentContext(editor)
      return currentContext.equals(metrics.fontRenderContext)
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
  }

  protected fun getFontMetrics(editor: Editor): MyFontMetrics {
    val familyName = UIManager.getFont("Label.font").family
    val size = Math.max(1, editor.colorsScheme.editorFontSize - 1)
    var metrics = editor.getUserData(HINT_FONT_METRICS)
    if (metrics != null && !metrics.isActual(editor, familyName, size)) {
      metrics = null
    }
    if (metrics == null) {
      metrics = MyFontMetrics(editor, familyName, size)
      editor.putUserData(HINT_FONT_METRICS, metrics)
    }
    return metrics
  }

  private fun getFont(editor: Editor): Font {
    return getFontMetrics(editor).font
  }

  protected fun doCalcWidth(text: String?, fontMetrics: FontMetrics): Int {
    return if (text == null) 0 else fontMetrics.stringWidth(text) + 14
  }

  companion object {
    private val HINT_FONT_METRICS = Key.create<MyFontMetrics>("ParameterHintFontMetrics")
    private const val BACKGROUND_ALPHA = 0.55f
  }
}
