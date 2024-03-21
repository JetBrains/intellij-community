// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

// TODO name
@ApiStatus.Internal
class InlineSuffixRenderer(private val editor: Editor, contents: List<InlineCompletionRenderTextBlock>) : EditorCustomElementRenderer {

  constructor(editor: Editor, text: String, color: Color = InlineCompletionFontUtils.color(editor)) : this(
    editor,
    listOf(InlineCompletionRenderTextBlock(text, color))
  )

  @Deprecated("Use contents")
  @ApiStatus.ScheduledForRemoval
  val suffix: String = contents.joinToString("") { it.text }

  val contents: List<InlineCompletionRenderTextBlock> = run {
    val tabSize = editor.settings.getTabSize(editor.project)
    contents.map { InlineCompletionRenderTextBlock(it.text.formatTabs(tabSize), it.color) }
  }

  private val widths: List<Int>
    get() {
      val fontMetrics = InlineCompletionFontUtils.fontMetrics(editor)
      return this.contents.map { fontMetrics.stringWidth(it.text) }
    }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = maxOf(1, widths.sum())

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = InlineCompletionFontUtils.fontMetrics(editor).height

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (contents.isEmpty()) {
      return
    }
    g.font = InlineCompletionFontUtils.font(editor)
    var x = targetRegion.x
    for ((i, content) in contents.withIndex()) {
      g.color = content.color
      g.drawString(content.text, x, targetRegion.y + editor.ascent)
      x += widths[i]
    }
  }
}
