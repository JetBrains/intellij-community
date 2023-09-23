// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.EffectPainter
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.RenderingHints
import kotlin.math.max

@Internal
sealed class InlayPresentationEntry(
  @TestOnly
  val clickArea: InlayMouseArea?
) {
  abstract fun render(
    graphics: Graphics2D,
    fontMetricsStorage: InlayTextMetricsStorage,
    attributes: TextAttributes,
    isDisabled: Boolean,
    yOffset: Int,
    rectHeight: Int,
    editor: Editor
  )

  abstract fun computeWidth(fontMetricsStorage: InlayTextMetricsStorage): Int

  abstract fun computeHeight(fontMetricsStorage: InlayTextMetricsStorage): Int

  abstract fun handleClick(editor: Editor, list: InlayPresentationList, controlDown: Boolean)

  var isHoveredWithCtrl: Boolean = false
}

class TextInlayPresentationEntry(
  @TestOnly
  val text: String,
  private val parentIndexToSwitch: Byte = -1,
  clickArea: InlayMouseArea?
) : InlayPresentationEntry(clickArea) {

  override fun handleClick(editor: Editor, list: InlayPresentationList, controlDown: Boolean) {
    val project = editor.project
    if (clickArea != null && project != null) {
      val actionData = clickArea.actionData
      if (controlDown) {
        val handlerId = actionData.handlerId
        val handler = InlayActionHandler.getActionHandler(handlerId)
        if (handler != null) {
          InlayActionHandlerUsagesCollector.clickHandled(handlerId, handler.javaClass)
          handler.handleClick(editor, actionData.payload)
        }
      }
    }
    if (parentIndexToSwitch != (-1).toByte()) {
      list.toggleTreeState(parentIndexToSwitch)
    }
  }

  override fun render(graphics: Graphics2D,
                      fontMetricsStorage: InlayTextMetricsStorage,
                      attributes: TextAttributes,
                      isDisabled: Boolean,
                      yOffset: Int,
                      rectHeight: Int,
                      editor: Editor) {
    val metrics = fontMetricsStorage.getFontMetrics(small = false)
    val savedHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
    val savedColor = graphics.color
    try {
      val foreground = attributes.foregroundColor
      if (foreground != null) {
        val width = computeWidth(fontMetricsStorage)
        val height = computeHeight(fontMetricsStorage)
        val font = metrics.font
        graphics.font = font
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
        graphics.color = foreground
        graphics.drawString(text, 0, max(editor.ascent, (rectHeight + metrics.ascent - metrics.descent) / 2) - 1)
        val effectColor = attributes.effectColor ?: foreground
        if (isDisabled) {
          graphics.color = effectColor
          EffectPainter.STRIKE_THROUGH.paint(graphics, 0, metrics.fontBaseline + yOffset, width, height, font)
        }
      }
    }
    finally {
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
      graphics.color = savedColor
    }
  }

  override fun computeWidth(fontMetricsStorage: InlayTextMetricsStorage): Int {
    return fontMetricsStorage.getFontMetrics(small = false).getStringWidth(text)
  }

  override fun computeHeight(fontMetricsStorage: InlayTextMetricsStorage): Int {
    return fontMetricsStorage.getFontMetrics(small = false).fontHeight
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
}