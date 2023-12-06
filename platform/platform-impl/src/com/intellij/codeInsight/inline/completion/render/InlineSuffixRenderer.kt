// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Rectangle

class InlineSuffixRenderer(private val editor: Editor, suffix: String) : EditorCustomElementRenderer {
  private val font = InlineCompletionFontUtils.font(editor)
  private val width = editor.contentComponent.getFontMetrics(font).stringWidth(suffix)

  val suffix = suffix.formatBeforeRendering(editor)

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = width
  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return editor.contentComponent.getFontMetrics(font).height
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    g.color = InlineCompletionFontUtils.color(editor)
    g.font = font
    g.drawString(suffix, targetRegion.x, targetRegion.y + editor.ascent)
  }
}
