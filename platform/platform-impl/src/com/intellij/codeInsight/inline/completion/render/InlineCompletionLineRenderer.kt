// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

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
  blocks: List<InlineCompletionRenderTextBlock>
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
    blocks.map { InlineCompletionRenderTextBlock(it.text.formatTabs(tabSize), it.attributes) }
  }

  private val widths: List<Int>
    get() = this.blocks.map {
      val font = editor.colorsScheme.getFont(EditorFontType.forJavaStyle(it.attributes.fontType))
      val fontMetrics = editor.contentComponent.getFontMetrics(font)
      fontMetrics.stringWidth(it.text)
    }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = maxOf(1, widths.sum())

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (blocks.isEmpty()) {
      return
    }

    val previousRenderingHint = (g as Graphics2D).getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))

    var x = targetRegion.x
    for ((block, width) in blocks.zip(widths)) {
      block.attributes.backgroundColor?.let {
        g.color = it
        g.fillRect(x, targetRegion.y, width, targetRegion.height)
      }
      g.color = block.attributes.foregroundColor
      g.font = editor.colorsScheme.getFont(EditorFontType.forJavaStyle(block.attributes.fontType))
      if (block.attributes.effectType != null && block.attributes.effectColor != null) {
        LOG.error("The effects are not supported in Inline Completion yet.") // TODO
      }
      g.drawString(block.text, x, targetRegion.y + editor.ascent)
      x += width
    }

    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, previousRenderingHint)
  }

  companion object {
    private val LOG = logger<InlineCompletionLineRenderer>()
  }
}
