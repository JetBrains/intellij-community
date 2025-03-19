package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point

@ApiStatus.Internal
interface ICodeVisionGraphicPainter : ICodeVisionPainter {
  fun paint(
    editor: Editor, textAttributes: TextAttributes,
    g: Graphics, point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean
  )

  fun size(editor: Editor, state: RangeCodeVisionModel.InlayState): Dimension
}