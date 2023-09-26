// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Experimental
class InlineBlockElementRenderer(private val editor: Editor, val lines: List<String>) : EditorCustomElementRenderer, InlineCompletionElementRenderer {

  private val width = editor
    .contentComponent
    .getFontMetrics(InlineFontUtils.font(editor))
    .stringWidth(lines.maxBy { it.length })

  override fun calcWidthInPixels(inlay: Inlay<*>) = width

  override fun calcHeightInPixels(inlay: Inlay<*>) = editor.lineHeight * lines.size

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    g.color = InlineFontUtils.color(editor)
    g.font = InlineFontUtils.font(editor)
    lines.forEachIndexed { i, it -> g.drawString(it, 0, targetRegion.y + editor.ascent + i * editor.lineHeight) }
  }
}
