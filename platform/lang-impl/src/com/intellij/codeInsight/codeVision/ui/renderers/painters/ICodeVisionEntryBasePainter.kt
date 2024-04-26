package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point

interface ICodeVisionEntryBasePainter<T> : ICodeVisionPainter {
  fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics, value: T, point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean,
    hoveredEntry: CodeVisionEntry?
  )

  fun size(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: T
  ): Dimension
}