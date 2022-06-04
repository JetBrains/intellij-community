package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionListPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionTheme
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

abstract class CodeVisionListRenderer(theme: CodeVisionTheme? = null) : CodeVisionRenderer {
  protected val painter = CodeVisionListPainter(theme = theme)

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (!inlay.isValid) return

    val userData = inlay.getUserData(CodeVisionListData.KEY)
    userData?.isPainted = true

    painter.paint(
      inlay.editor,
      textAttributes,
      g,
      inlay.getUserData(CodeVisionListData.KEY),
      getPoint(inlay, targetRegion.location),
      userData?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL,
      userData?.isMoreLensActive() ?: false
    )
  }

  protected open fun getPoint(inlay: Inlay<*>, targetPoint: Point): Point = targetPoint

  protected fun inlayState(inlay: Inlay<*>) =
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL

}