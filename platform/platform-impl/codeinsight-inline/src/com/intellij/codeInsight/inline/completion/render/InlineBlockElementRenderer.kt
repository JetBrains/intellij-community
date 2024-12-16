// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@Deprecated("All rendering uses InlineCompletionLineRenderer")
@ApiStatus.ScheduledForRemoval
@ApiStatus.Internal
class InlineBlockElementRenderer(private val editor: Editor, lines: List<String>) : EditorCustomElementRenderer {

  val lines: List<String> = run {
    val tabSize = editor.settings.getTabSize(editor.project)
    lines.map { it.formatTabs(tabSize) }
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int = lines.maxOf {
    InlineCompletionFontUtils.fontMetrics(editor).stringWidth(it)
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int = editor.lineHeight * lines.size

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    g.color = InlineCompletionFontUtils.color(editor)
    g.font = InlineCompletionFontUtils.font(editor)
    lines.forEachIndexed { i, it -> g.drawString(it, 0, targetRegion.y + editor.ascent + i * editor.lineHeight) }
  }
}
