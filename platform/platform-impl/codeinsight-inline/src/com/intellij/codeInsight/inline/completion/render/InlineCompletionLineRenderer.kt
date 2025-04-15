// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils.getFont
import com.intellij.codeInsight.inline.completion.render.InlineCompletionVolumetricTextBlockFactory.Companion.accumulatedWidthToInt
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.EffectPainter2D
import com.intellij.ui.paint.RectanglePainter2D
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.font.TextLayout

/**
 * Should not be used outside rendering the default inline completion elements.
 *
 * For now, it supports:
 * * [TextAttributes.getForegroundColor]
 * * [TextAttributes.getFontType]
 */
@ApiStatus.Internal
@ApiStatus.NonExtendable
open class InlineCompletionLineRenderer(
  private val editor: Editor,
  initialBlocks: List<InlineCompletionRenderTextBlock>
) : EditorCustomElementRenderer {

  constructor(editor: Editor, text: String, attributes: TextAttributes = InlineCompletionFontUtils.attributes(editor)) : this(
    editor,
    listOf(InlineCompletionRenderTextBlock(text, attributes))
  )

  private var isDirty = false

  var blocks: List<InlineCompletionRenderTextBlock> = formatTabs(initialBlocks)
    internal set(newBlocks) {
      ThreadingAssertions.assertEventDispatchThread()
      isDirty = true
      field = newBlocks
    }

  fun updateIfNeeded(inlay: Inlay<out InlineCompletionLineRenderer>) {
    ThreadingAssertions.assertEventDispatchThread()
    check(inlay.renderer === this)
    if (isDirty) {
      inlay.update()
    }
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val result = InlineCompletionVolumetricTextBlockFactory(editor).use { volumetricFactory ->
      blocks.sumOf { block -> volumetricFactory.getVolumetric(block).widthInPixels }
    }
    return maxOf(1, accumulatedWidthToInt(result))
  }

  protected open fun beforePaint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle) {}

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (blocks.isEmpty()) {
      return
    }

    beforePaint(inlay, g, targetRegion)

    val previousRenderingHint = (g as Graphics2D).getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))

    // We need to use doubles instead of integers because a width of a single block can be non-integer.
    // If a line has lots of blocks, we round up integers too much, so line looks wider than it should be.

    var x = targetRegion.x.toDouble()
    for (block in blocks) {
      if (block.text.isEmpty()) {
        continue
      }
      g.font = getFont(editor, block.text, block.attributes.fontType)
      val textLayout = TextLayout(block.text, g.font, g.fontRenderContext)
      val textWidth = textLayout.advance.toDouble()
      val textHeight = targetRegion.height.toDouble()
      val topY = targetRegion.y
      val bottomY = topY + editor.ascent

      paintBackground(g, block.attributes.backgroundColor, x, topY.toDouble(), textWidth, textHeight)
      paintEffect(g, block.attributes.effectColor, block.attributes.effectType, x, bottomY.toDouble(), textWidth)

      g.color = block.attributes.foregroundColor
      textLayout.draw(g, x.toFloat(), bottomY.toFloat())
      x += textWidth
    }

    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, previousRenderingHint)
  }

  private fun paintBackground(
    g: Graphics2D,
    color: Color?,
    x: Double,
    y: Double,
    width: Double,
    height: Double
  ) {
    if (color == null) return
    g.color = color
    RectanglePainter2D.FILL.paint(g, x, y, width, height)
  }

  private fun paintEffect(
    g: Graphics2D,
    color: Color?,
    effectType: EffectType?,
    x: Double,
    y: Double,
    width: Double
  ) {
    if (color == null || effectType == null) return
    val painter = when (effectType) {
      EffectType.LINE_UNDERSCORE -> EffectPainter2D.LINE_UNDERSCORE
      EffectType.WAVE_UNDERSCORE -> EffectPainter2D.WAVE_UNDERSCORE
      EffectType.STRIKEOUT -> EffectPainter2D.STRIKE_THROUGH
      EffectType.BOLD_LINE_UNDERSCORE -> EffectPainter2D.BOLD_LINE_UNDERSCORE
      EffectType.BOLD_DOTTED_LINE -> EffectPainter2D.BOLD_DOTTED_UNDERSCORE
      else -> null
    }

    if (painter != null) {
      g.color = color
      painter.paint(g, x, y, width, g.fontMetrics.descent.toDouble(), g.font)
    }
  }

  private fun formatTabs(blocks: List<InlineCompletionRenderTextBlock>): List<InlineCompletionRenderTextBlock> {
    val tabSize = editor.settings.getTabSize(editor.project)
    return blocks.filter { it.text.isNotEmpty() }.map { it.copy(text = it.text.formatTabs(tabSize)) }
  }
}
