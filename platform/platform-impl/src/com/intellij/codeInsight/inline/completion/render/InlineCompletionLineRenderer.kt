// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.RectanglePainter2D
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.TextLayout

/**
 * Should not be used outside rendering the default inline completion elements.
 *
 * For now, it supports:
 * * [TextAttributes.getForegroundColor]
 * * [TextAttributes.getFontType]
 */
@ApiStatus.Internal
class InlineCompletionLineRenderer(
  private val editor: Editor,
  initialBlocks: List<InlineCompletionRenderTextBlock>
) : EditorCustomElementRenderer {

  constructor(editor: Editor, text: String, attributes: TextAttributes = InlineCompletionFontUtils.attributes(editor)) : this(
    editor,
    listOf(InlineCompletionRenderTextBlock(text, attributes))
  )

  val suffix: String
    @Deprecated("Use blocks")
    @ApiStatus.ScheduledForRemoval
    get() = blocks.joinToString("") { it.text }

  val blocks: List<InlineCompletionRenderTextBlock> = run {
    val tabSize = editor.settings.getTabSize(editor.project)
    initialBlocks.filter { it.text.isNotEmpty() }.map { InlineCompletionRenderTextBlock(it.text.formatTabs(tabSize), it.attributes) }
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val result = blocks.sumOf { block ->
      val font = editor.colorsScheme.getFont(EditorFontType.forJavaStyle(block.attributes.fontType))
      val fontMetrics = editor.contentComponent.getFontMetrics(font)
      fontMetrics.stringWidth(block.text)
    }
    return maxOf(1, result)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (blocks.isEmpty()) {
      return
    }

    val previousRenderingHint = (g as Graphics2D).getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))

    // We need to use doubles instead of integers because a width of a single block can be non-integer.
    // If a line has lots of blocks, we round up integers too much, so line looks wider than it should be.

    var x = targetRegion.x.toDouble()
    for (block in blocks) {
      if (block.text.isEmpty()) {
        continue
      }
      g.font = editor.colorsScheme.getFont(EditorFontType.forJavaStyle(block.attributes.fontType))
      val textLayout = TextLayout(block.text, g.font, g.fontRenderContext)
      val textWidth = textLayout.advance.toDouble()
      block.attributes.backgroundColor?.let {
        g.color = it
        RectanglePainter2D.FILL.paint(g, x, targetRegion.y.toDouble(), textWidth, targetRegion.height.toDouble())
      }
      g.color = block.attributes.foregroundColor
      textLayout.draw(g, x.toFloat(), (targetRegion.y + editor.ascent).toFloat())
      x += textWidth
    }

    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, previousRenderingHint)
  }
}
