// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused", "ForEachParameterNotUsed")

package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import kotlinx.coroutines.FlowPreview
import java.awt.Point
import java.awt.Rectangle

/**
 * We do not use this code now but it can be used for optimization so we keep it
 */
@OptIn(FlowPreview::class)
class NotebookVisibleCellsBatchUpdater(
  private val editor: EditorImpl,
) : Disposable.Default {
  private var prevVisibleCells = listOf<EditorCell>()

  init {
    JupyterBoundsChangeHandler.get(editor).subscribe(this, object : JupyterBoundsChangeListener {
      override fun boundsChanged() {
        updateVisibleCells()
      }
    })
    editor.scrollingModel.addVisibleAreaListener(
      {
        if (it.newRectangle == it.oldRectangle)
          return@addVisibleAreaListener
        updateVisibleCells()
      }, this)
    updateVisibleCells()
  }

  fun isCellVisible(cell: EditorCell): Boolean {
    val cached = prevVisibleCells.contains(cell)
    if (cached)
      return true
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.height == 0 || visibleArea.width == 0)
      return false

    val cellRectangle = getCellRectangle(cell) ?: return false
    return visibleArea.intersects(cellRectangle)
  }

  private fun getCellRectangle(cell: EditorCell): Rectangle? {
    val interval = cell.intervalOrNull ?: return null


    val firstLineWithOverlap = maxOf(interval.firstContentLine - 1, 0)
    val lastLineWithOverlap = minOf(interval.lastContentLine + 1, editor.document.lineCount)

    val startY = editor.logicalPositionToXY(LogicalPosition(firstLineWithOverlap, 0)).y
    val endY = editor.logicalPositionToXY(LogicalPosition(lastLineWithOverlap, 0)).y + editor.lineHeight

    val cellRectangle = Rectangle(0, startY, editor.component.width, endY - startY)

    return cellRectangle
  }

  private fun updateVisibleCells() {
    val inlayManager = NotebookCellInlayManager.get(editor) ?: return
    val visibleArea = editor.scrollingModel.visibleArea

    if (visibleArea.height == 0 || visibleArea.width == 0) {
      prevVisibleCells.forEach {
        //it.isInViewportRectangle.set(false)
      }
      prevVisibleCells = emptyList()
      return
    }

    //Give a little bit more to be aware that custom render foldings are included
    val firstVisibleLine = maxOf(editor.xyToLogicalPosition(Point(0, visibleArea.y)).line, 0)
    val lastVisibleLine = minOf(editor.xyToLogicalPosition(Point(0, visibleArea.y + visibleArea.height)).line + 1, editor.document.lineCount - 1)

    val visibleCells = mutableListOf<EditorCell>()
    for (cell in inlayManager.cells) {
      val firstLine = cell.interval.firstContentLine
      val lastLine = cell.interval.lastContentLine

      // The cell is above the visible area, go to next
      if (lastLine < firstVisibleLine) continue
      // This cell is below the visible area, stop iteration - they all are for sure outside the visible area
      if (firstLine > lastVisibleLine) break
      visibleCells.add(cell)
    }

    if (visibleCells == prevVisibleCells) return
    val newInvisible = prevVisibleCells - visibleCells
    val newVisible = visibleCells - prevVisibleCells
    newInvisible.forEach {
      //it.isInViewportRectangle.set(false)
    }
    newVisible.forEach {
      //it.isInViewportRectangle.set(true)
    }

    prevVisibleCells = visibleCells
  }

  companion object {
    private val INSTANCE_KEY = Key.create<NotebookVisibleCellsBatchUpdater>("EDITOR_CELL_FRAME_UPDATER_KEY")

    fun install(editor: EditorImpl) {
      //val updater = NotebookVisibleCellsBatchUpdater(editor)
      //editor.putUserData(INSTANCE_KEY, updater)
      //Disposer.register(editor.disposable, updater)
    }
  }
}