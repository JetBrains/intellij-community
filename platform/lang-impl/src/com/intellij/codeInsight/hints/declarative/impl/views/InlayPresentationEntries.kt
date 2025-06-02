// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.RenderingHints
import kotlin.math.max

@Internal
sealed class InlayPresentationEntry(
  @TestOnly
  val clickArea: InlayMouseArea?,
  val parentIndexToSwitch: Byte,
) : InlayElementWithMargins<InlayTextMetrics>, Invalidable {
  abstract fun render(
    graphics: Graphics2D,
    metrics: InlayTextMetrics,
    attributes: TextAttributes,
    isDisabled: Boolean,
    yOffset: Int,
    rectHeight: Int,
    editor: Editor,
  )

  var isHoveredWithCtrl: Boolean = false
}

@Internal
class TextInlayPresentationEntry(
  @TestOnly
  val text: String,
  parentIndexToSwitch: Byte = -1,
  clickArea: InlayMouseArea?,
) : InlayPresentationEntry(clickArea, parentIndexToSwitch) {

  override fun render(
    graphics: Graphics2D,
    metrics: InlayTextMetrics,
    attributes: TextAttributes,
    isDisabled: Boolean,
    yOffset: Int,
    rectHeight: Int,
    editor: Editor,
  ) {
    val savedHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
    val savedColor = graphics.color
    try {
      val foreground = attributes.foregroundColor
      if (foreground != null) {
        val font = metrics.font
        graphics.font = font
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
        graphics.color = foreground
        val baseline = max(editor.ascent, (rectHeight + metrics.ascent - metrics.descent) / 2) - 1
        graphics.drawString(text, 0, baseline)
      }
    }
    finally {
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
      graphics.color = savedColor
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TextInlayPresentationEntry

    return text == other.text
  }

  override fun hashCode(): Int {
    return text.hashCode()
  }

  override fun toString(): String {
    return "TextInlayPresentationEntry(text='$text', parentIndexToSwitch=$parentIndexToSwitch)"
  }

  override fun computeLeftMargin(context: InlayTextMetrics): Int = 0
  override fun computeRightMargin(context: InlayTextMetrics): Int = 0

  override fun computeBoxWidth(context: InlayTextMetrics): Int = context.getStringWidth(text)

  override fun invalidate() {}
}
