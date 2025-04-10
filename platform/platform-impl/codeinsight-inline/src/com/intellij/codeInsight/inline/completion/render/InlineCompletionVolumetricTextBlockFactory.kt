// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.openapi.editor.Editor
import com.intellij.ui.paint.PaintUtil
import java.awt.Font
import java.awt.FontMetrics
import java.awt.font.TextLayout
import java.lang.AutoCloseable

// instance must live within one short session when anything in editor's font can change
internal class InlineCompletionVolumetricTextBlockFactory(private val editor: Editor) : AutoCloseable {

  private val fontToMetrics = mutableMapOf<Font, FontMetrics>()

  fun getVolumetric(block: InlineCompletionRenderTextBlock): VolumetricInlineCompletionTextBlock {
    if (block.text.isEmpty()) {
      return VolumetricInlineCompletionTextBlock(block, 0.0)
    }
    val font = InlineCompletionFontUtils.getFont(editor, block.text, block.attributes.fontType)
    val fontMetrics = fontToMetrics.getOrPut(font) { editor.contentComponent.getFontMetrics(font) }
    val width = TextLayout(block.text, font, fontMetrics.fontRenderContext).advance.toDouble()
    return VolumetricInlineCompletionTextBlock(block, width)
  }

  override fun close() {
    fontToMetrics.clear()
  }
  
  companion object {
    internal fun accumulatedWidthToInt(width: Double): Int {
      return PaintUtil.RoundingMode.CEIL.round(width + 0.5)
    }
  }
}

internal fun List<InlineCompletionRenderTextBlock>.toVolumetric(
  factory: InlineCompletionVolumetricTextBlockFactory
): List<VolumetricInlineCompletionTextBlock> {
  return map { factory.getVolumetric(it) }
}
