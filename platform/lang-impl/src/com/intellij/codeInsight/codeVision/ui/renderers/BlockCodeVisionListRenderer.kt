package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.openapi.editor.Inlay
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import java.awt.Point
import java.awt.Rectangle

class BlockCodeVisionListRenderer : CodeVisionListRenderer() {

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val userData = inlay.getUserData(CodeVisionListData.KEY)
    if (userData?.isPainted == false) {
      return 0
    }

    val painterPosition = painterPosition(inlay)
    return painter.size(inlay.editor, inlayState(inlay), inlay.getUserData(CodeVisionListData.KEY)).width + painterPosition
  }


  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return painter.size(inlay.editor, inlayState(inlay), inlay.getUserData(CodeVisionListData.KEY)).height
  }

  override fun getPoint(inlay: Inlay<*>, targetPoint: Point): Point {
    val painterPosition = painterPosition(inlay)
    return Point(targetPoint.x + painterPosition, targetPoint.y)
  }

  override fun hoveredEntry(inlay: Inlay<*>, x: Int, y: Int): CodeVisionEntry? {
    return painter.hoveredEntry(inlay.editor, inlayState(inlay), inlay.getUserData(CodeVisionListData.KEY), x - painterPosition(inlay), y)
  }

  override fun entryBounds(inlay: Inlay<*>, element: CodeVisionEntry): Rectangle? {
    val hoveredEntryBounds = painter.hoveredEntryBounds(inlay.editor, inlayState(inlay), inlay.getUserData(CodeVisionListData.KEY), element)
                             ?: return null
    hoveredEntryBounds.x += painterPosition(inlay)
    return hoveredEntryBounds
  }

  private fun painterPosition(inlay: Inlay<*>): Int {
    if (!inlay.isValid) return 0

    val editor = inlay.editor
    val lineStartOffset = DocumentUtil.getLineStartOffset(inlay.offset, editor.document)

    val shiftForward = CharArrayUtil.shiftForward(editor.document.immutableCharSequence, lineStartOffset, " \t")

    return editor.offsetToXY(shiftForward).x
  }

}