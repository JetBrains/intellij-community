package com.intellij.codeInsight.completion.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Internal
class InlineSuffixRenderer(private val editor: Editor, val suffix: String) : EditorCustomElementRenderer {
  private val width = editor.contentComponent.getFontMetrics(InlineFontUtils.font(editor)).stringWidth(suffix)

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return width
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return editor.contentComponent.getFontMetrics(InlineFontUtils.font(editor)).height
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    g.color = InlineFontUtils.color
    g.font = InlineFontUtils.font(editor)
    g.drawString(suffix, targetRegion.x, targetRegion.y + editor.ascent)
  }
}
