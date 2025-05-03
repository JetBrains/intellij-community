// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.frame

import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellLines.CellType
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.FlowPreview
import java.awt.geom.Line2D

@OptIn(FlowPreview::class)
class EditorCellFrameManager(private val editorCell: EditorCell) : Disposable {  // PY-74106
  private val editor
    get() = editorCell.editor
  private val cellType
    get() = editorCell.intervalOrNull?.type
  private val view
    get() = editorCell.view



  private val isSelected
    get() = editorCell.isSelected.get()
  private val isHovered
    get() = editorCell.isHovered.get()

  val state: AtomicProperty<CellFrameState> = AtomicProperty(CellFrameState())

  private var cachedRightLine: Line2D? = null

  init {
    editorCell.isSelected.afterDistinctChange(this) {
      updateCellFrameShow()
    }
    editorCell.isHovered.afterDistinctChange(this) {
      updateCellFrameShow()
    }

    editorCell.isUnfolded.afterDistinctChange(this) {
      updateCellFrameShow()
    }


    editor.notebookAppearance.cellFrameSelectedColor.afterChange(this) {
      updateCellFrameShow()
    }

    editor.notebookAppearance.cellFrameHoveredColor.afterChange(this) {
      updateCellFrameShow()
    }

    editor.notebookAppearance.editorBackgroundColor.afterChange(this) {
      updateCellFrameShow()
    }

    editor.notebookAppearance.codeCellBackgroundColor.afterChange(this) {
      updateCellFrameShow()
    }

    JupyterBoundsChangeHandler.get(editor).subscribe(this) {
      cachedRightLine = null
    }

    updateCellFrameShow()
  }

  override fun dispose() {
    state.set(CellFrameState(false))
  }

  fun getOrCalculateLineFrameVerticalLine(): Line2D? {
    if (state.get().isVisible.not())
      return null

    cachedRightLine?.let {
      return it
    }
    return calculateRightLine()
  }

  private fun calculateRightLine(): Line2D.Double? {
    val inlays = view?.input?.getBlockElementsInRange() ?: return null
    val upperInlayBounds = inlays.firstOrNull {
      it.properties.priority == editor.notebookAppearance.cellInputInlaysPriority && it.properties.isShownAbove
    }?.bounds ?: return null

    val bottomRectHeight = editor.notebookAppearance.cellBorderHeight / 2
    val delimiterHeight = upperInlayBounds.height - bottomRectHeight
    val topPosition = upperInlayBounds.y + delimiterHeight

    val lowerInlayBounds = inlays.lastOrNull {
      it.properties.priority == editor.notebookAppearance.cellInputInlaysPriority && !it.properties.isShownAbove
    }?.bounds ?: return null

    val lineX = upperInlayBounds.x + upperInlayBounds.width - 0.5
    val lineStartY = (topPosition).toDouble()
    val lineEndY = (lowerInlayBounds.y + lowerInlayBounds.height).toDouble()

    val line2DDouble = Line2D.Double(lineX, lineStartY, lineX, lineEndY)
    cachedRightLine = line2DDouble
    return line2DDouble
  }

  fun updateCellFrameShow() {
    if (cellType == CellType.MARKDOWN) {
      updateCellFrameShowMarkdown()
    }
    else {
      updateCellFrameShowCode()
    }
  }

  private fun updateCellFrameShowMarkdown() {
    if (editorCell.isUnderDiff.get()) {
      // under diff, it is necessary to make the selected cell more visible with blue frame for md cells
      updateCellFrameShowCode()
      return
    }

    when {
      isSelected -> {
        state.set(CellFrameState(true, editor.notebookAppearance.cellFrameSelectedColor.get()))
      }
      isHovered -> {
        state.set(CellFrameState(true, editor.notebookAppearance.cellFrameHoveredColor.get()))
      }
      else -> {
        state.set(CellFrameState(false))
      }
    }
  }

  private fun updateCellFrameShowCode() {
    if (isSelected) {
      state.set(CellFrameState(true, editor.notebookAppearance.cellFrameSelectedColor.get()))
    }
    else {
      state.set(CellFrameState(false))
    }
  }


  companion object {
    fun create(editorCell: EditorCell): EditorCellFrameManager? =
      if (editorCell.interval.type == CellType.MARKDOWN && Registry.`is`("jupyter.markdown.cells.border") ||
          Registry.`is`("jupyter.code.cells.border")) {
        EditorCellFrameManager(editorCell)
      }
      else {
        null
      }
  }
}