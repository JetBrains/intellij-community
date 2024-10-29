// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.EffectPainter2D
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*

@ApiStatus.Internal
class CodeVisionRichTextPainter<T>(
  val printer: (T) -> RichText,
  theme: CodeVisionTheme? = null
) : ICodeVisionEntryBasePainter<T> {

  val theme: CodeVisionTheme = theme ?: CodeVisionTheme()

  companion object {
    private val logger = Logger.getInstance(CodeVisionRichTextPainter::class.java)
  }

  override fun paint(editor: Editor,
                     textAttributes: TextAttributes,
                     g: Graphics,
                     value: T,
                     point: Point,
                     state: RangeCodeVisionModel.InlayState,
                     hovered: Boolean,
                     hoveredEntry: CodeVisionEntry?) {
    g as Graphics2D

    val richSegments = printer.invoke(value).parts
    val themeInfoProvider = service<CodeVisionThemeInfoProvider>()

    val inSelectedBlock = textAttributes.backgroundColor == editor.selectionModel.textAttributes.backgroundColor
    g.color = if (inSelectedBlock) editor.selectionModel.textAttributes.foregroundColor ?: editor.colorsScheme.defaultForeground
    else {
      themeInfoProvider.foregroundColor(editor, hovered)
    }

    val x = point.x + theme.left
    val y = point.y + theme.top

    var xOffset = x

    var underlineColor: Color? = null
    richSegments.forEach {
      if (it.attributes.bgColor != null) logger.error("Rich text renderer doesn't support background colors currently")
      if (it.attributes.waveColor != null) logger.error("Rich text renderer doesn't support effect colors currently")

      val foregroundColor = it.attributes.fgColor
      if (underlineColor == null) underlineColor = foregroundColor
      else if(underlineColor != foregroundColor) underlineColor = g.color
      val font = themeInfoProvider.font(editor, it.attributes.fontStyle)
      g.font = font
      withColor(g, foregroundColor) {
        g.drawString(it.text, xOffset, y)
      }
      val metrics = g.fontMetrics
      if (it.attributes.isStrikeout) {
        withColor(g, foregroundColor) {
          EffectPainter2D.STRIKE_THROUGH.paint(g, xOffset.toDouble(), (y + JBUI.scale(1)).toDouble(), metrics.stringWidth(it.text).toDouble(), 5.0, g.font)
        }
      }
      xOffset += metrics.stringWidth(it.text)
    }

    if (hovered) {
      val size = size(editor, state, value)
      withColor(g, underlineColor) {
        EffectPainter2D.LINE_UNDERSCORE.paint(g, x.toDouble(), (y + JBUI.scale(1)).toDouble(), size.width.toDouble(), 5.0, g.font)
      }
    }
  }

  private inline fun withColor(g: Graphics2D, targetColor: Color?, block: () -> Unit){
    val curColor = g.color
    g.color = targetColor ?: g.color
    block.invoke()
    g.color = curColor
  }

  override fun size(editor: Editor, state: RangeCodeVisionModel.InlayState, value: T): Dimension {
    val richSegments = printer.invoke(value).parts
    var width = theme.left
    var height = theme.top
    val themeInfoProvider = service<CodeVisionThemeInfoProvider>()

    richSegments.forEach {
      val font = themeInfoProvider.font(editor, it.attributes.fontStyle)
      val metrics = editor.component.getFontMetrics(font)
      width += metrics.stringWidth(it.text)
      height = maxOf(height, metrics.height)
    }

    return Dimension(
      width + theme.right,
      height + theme.bottom
    )
  }
}