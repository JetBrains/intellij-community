// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle

class InlineBlockElementRenderer(private val editor: Editor, val lines: List<String>) : EditorCustomElementRenderer {

  private val width = editor
    .contentComponent
    .getFontMetrics(InlineCompletionFontUtils.font(editor))
    .stringWidth(lines.maxBy { it.length })

  override fun calcWidthInPixels(inlay: Inlay<*>) = width

  override fun calcHeightInPixels(inlay: Inlay<*>) = editor.lineHeight * lines.size

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    g.color = InlineCompletionFontUtils.color(editor)
    g.font = InlineCompletionFontUtils.font(editor)
    lines.forEachIndexed { i, it -> g.drawString(it, 0, targetRegion.y + editor.ascent + i * editor.lineHeight) }
  }
}
