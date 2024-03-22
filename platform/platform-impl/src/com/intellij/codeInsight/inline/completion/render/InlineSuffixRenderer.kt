// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

// TODO name
// TODO what attributes are supported
@ApiStatus.Internal
class InlineSuffixRenderer(private val editor: Editor, blocks: List<InlineCompletionRenderTextBlock>) : EditorCustomElementRenderer {

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
    get() {
      val fontMetrics = InlineCompletionFontUtils.fontMetrics(editor)
      return this.blocks.map { fontMetrics.stringWidth(it.text) }
    }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = maxOf(1, widths.sum())

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = InlineCompletionFontUtils.fontMetrics(editor).height

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (blocks.isEmpty()) {
      return
    }
    val baseFont = InlineCompletionFontUtils.font(editor)
    var x = targetRegion.x
    for ((i, block) in blocks.withIndex()) {
      g.font = baseFont
      g.setAttributes(block.attributes)
      g.drawString(block.text, x, targetRegion.y + editor.ascent)
      x += widths[i]
    }
  }

  private fun Graphics.setAttributes(attributes: TextAttributes) {
    setColor(attributes)
    setFont(attributes)
    assertEffects(attributes)
  }

  private fun Graphics.setColor(attributes: TextAttributes) {
    color = attributes.foregroundColor
  }

  private fun Graphics.setFont(attributes: TextAttributes) {
    if (attributes.fontType != font.style) {
      font = font.deriveFont(attributes.fontType)
    }
  }

  private fun assertEffects(attributes: TextAttributes) {
    if (attributes.effectType != null && attributes.effectColor != null) {
      LOG.error("The effects are not supported in Inline Completion yet.") // TODO
    }
  }

  companion object {
    private val LOG = logger<InlineSuffixRenderer>()
  }
}
