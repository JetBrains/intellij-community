// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayActionService
import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
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
    metrics: InlayTextMetrics,
    attributes: TextAttributes,
    isDisabled: Boolean,
    yOffset: Int,
    rectHeight: Int,
    editor: Editor
  )

  abstract fun computeWidth(metrics: InlayTextMetrics): Int

  abstract fun computeHeight(metrics: InlayTextMetrics): Int

  abstract fun handleClick(e: EditorMouseEvent, list: InlayPresentationList, controlDown: Boolean)

  var isHoveredWithCtrl: Boolean = false
}

@Internal
class TextInlayPresentationEntry(
  @TestOnly
  val text: String,
  private val parentIndexToSwitch: Byte = -1,
  clickArea: InlayMouseArea?
) : InlayPresentationEntry(clickArea) {

  override fun handleClick(e: EditorMouseEvent, list: InlayPresentationList, controlDown: Boolean) {
    val editor = e.editor
    val project = editor.project
    if (clickArea != null && project != null) {
      val actionData = clickArea.actionData
      if (controlDown) {
        service<DeclarativeInlayActionService>().invokeActionHandler(actionData, e)
      }
    }
    if (parentIndexToSwitch != (-1).toByte()) {
      list.toggleTreeState(parentIndexToSwitch)
    }
  }

  override fun render(graphics: Graphics2D,
                      metrics: InlayTextMetrics,
                      attributes: TextAttributes,
                      isDisabled: Boolean,
                      yOffset: Int,
                      rectHeight: Int,
                      editor: Editor) {
    val savedHint = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
    val savedColor = graphics.color
    try {
      val foreground = attributes.foregroundColor
      if (foreground != null) {
        val width = computeWidth(metrics)
        val height = computeHeight(metrics)
        val font = metrics.font
        graphics.font = font
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
        graphics.color = foreground
        val baseline = max(editor.ascent, (rectHeight + metrics.ascent - metrics.descent) / 2) - 1
        graphics.drawString(text, 0, baseline)
        val effectColor = attributes.effectColor ?: foreground
        if (isDisabled) {
          graphics.color = effectColor
          EffectPainter.STRIKE_THROUGH.paint(graphics, 0, baseline + yOffset, width, height, font)
        }
      }
    }
    finally {
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, savedHint)
      graphics.color = savedColor
    }
  }

  override fun computeWidth(metrics: InlayTextMetrics): Int = metrics.getStringWidth(text)

  override fun computeHeight(metrics: InlayTextMetrics): Int = metrics.fontHeight

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
