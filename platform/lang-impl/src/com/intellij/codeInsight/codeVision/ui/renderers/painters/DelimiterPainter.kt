package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point

@ApiStatus.Internal
class DelimiterPainter : ICodeVisionGraphicPainter {
  override fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics,
    point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean
  ) {
  }

  override fun size(editor: Editor, state: RangeCodeVisionModel.InlayState): Dimension {
    val width = service<CodeVisionThemeInfoProvider>().lensFontSize(editor)
    return Dimension(JBUI.scale(width.toInt()), editor.lineHeight)
  }
}