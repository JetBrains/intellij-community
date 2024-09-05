package com.intellij.notebooks.visualization.r.inlays.components

import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter
import java.awt.Graphics

object EmptySoftWrapPainter : SoftWrapPainter {
  override fun paint(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int) = 0

  override fun getDrawingHorizontalOffset(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int) = 0

  override fun getMinDrawingWidth(drawingType: SoftWrapDrawingType) = 0

  override fun canUse() = true

  override fun reinit() {}
}