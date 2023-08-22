package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionListPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionTheme
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

abstract class CodeVisionInlayRendererBase(theme: CodeVisionTheme = CodeVisionTheme()) : CodeVisionInlayRenderer {
  private var isHovered = false
  private var hoveredEntry: CodeVisionEntry? = null
  protected val painter: CodeVisionListPainter = CodeVisionListPainter(theme = theme)
  lateinit var inlay: Inlay<*>

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
      isHovered && userData?.isMoreLensActive() == true,
      hoveredEntry
    )
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    updateMouseState(true, translated)
  }

  override fun mouseExited() {
    updateMouseState(false, null)
  }

  override fun mousePressed(event: MouseEvent, translated: Point) {
    val clickedEntry = hoveredEntry ?: return
    when {
      event.isShiftDown -> return
      SwingUtilities.isLeftMouseButton(event) -> handleLeftClick(clickedEntry)
      SwingUtilities.isRightMouseButton(event) -> handleRightClick(clickedEntry)
    }
    event.consume()
  }

  private fun handleRightClick(clickedEntry: CodeVisionEntry) {
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.handleLensRightClick(clickedEntry, inlay)
  }

  private fun handleLeftClick(clickedEntry: CodeVisionEntry) {
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.handleLensClick(clickedEntry, inlay)
  }

  private fun updateMouseState(isHovered: Boolean, point: Point?) {
    this.isHovered = isHovered
    hoveredEntry = if (isHovered) getHoveredEntry(point) else null
    updateCursor(isHovered)
    inlay.repaint()
  }

  private fun getHoveredEntry(point: Point?): CodeVisionEntry? {
    val codeVisionListData = inlay.getUserData(CodeVisionListData.KEY)
    val state = codeVisionListData?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL
    return point?.let { painter.hoveredEntry(inlay.editor, state, codeVisionListData, it.x, it.y) }
  }

  private fun updateCursor(hasHoveredEntry: Boolean) {
    val cursor =
      if (hasHoveredEntry) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      else Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

    if (inlay.editor.contentComponent.cursor != cursor)
      UIUtil.setCursor(inlay.editor.contentComponent, cursor)
  }

  protected open fun getPoint(inlay: Inlay<*>, targetPoint: Point): Point = targetPoint

  protected fun inlayState(inlay: Inlay<*>): RangeCodeVisionModel.InlayState =
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL
}