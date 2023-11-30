package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionTheme
import com.intellij.openapi.editor.Inlay
import com.intellij.util.ui.JBUI
import java.awt.Rectangle

class InlineCodeVisionInlayRenderer : CodeVisionInlayRendererBase(CodeVisionTheme(left = JBUI.scale(5), right = JBUI.scale(5))) {
  override fun calculateCodeVisionEntryBounds(element: CodeVisionEntry): Rectangle? {
    return painter.hoveredEntryBounds(inlay.editor, inlayState(inlay), inlay.getUserData(CodeVisionListData.KEY), element)
  }


  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val userData = inlay.getUserData(CodeVisionListData.KEY)
    return painter.size(inlay.editor, inlayState(inlay), userData).width
  }
}