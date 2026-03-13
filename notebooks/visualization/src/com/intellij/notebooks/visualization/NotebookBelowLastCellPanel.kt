// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.notebooks.ui.visualization.NotebookUtil.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightable
import com.intellij.notebooks.visualization.ui.jupyterToolbars.JupyterAddNewCellToolbar
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

/**
 * Basically, this panel consists only of an "add new cell" toolbar
 * and highlightable border to show drop destination.
 */
class NotebookBelowLastCellPanel(
  val editor: EditorImpl,
) : JPanel(BorderLayout()), DropHighlightable {

  private var isHighlighted = false

  private val toolbar = JupyterAddNewCellToolbar(ActionUtil.getActionGroup("Jupyter.CreateNewCellsPanel")!!,
                                                 toolbarTargetComponent = this@NotebookBelowLastCellPanel)

  init {
    if (editor.isOrdinaryNotebookEditor()) {
      cursor = Cursor.getDefaultCursor()
      isOpaque = false
      border = HighlightableTopBorder(editor.notebookAppearance.cellBorderHeight)
      toolbar.background = editor.notebookAppearance.editorBackgroundColor()

      add(panel {
        row {
          cell(toolbar).align(Align.CENTER)
        }
      }.apply { isOpaque = false }, BorderLayout.CENTER)
    }
  }

  override fun updateUI() {
    @Suppress("SENSELESS_COMPARISON")
    if (editor != null) {
      toolbar.background = editor.notebookAppearance.editorBackgroundColor()
    }
  }

  override fun addDropHighlight() {
    isHighlighted = true
    repaint()
  }

  override fun removeDropHighlight() {
    isHighlighted = false
    repaint()
  }

  private inner class HighlightableTopBorder(private val borderHeight: Int) : JBEmptyBorder(borderHeight, 0, 0, 0) {
    override fun paintBorder(c: Component?, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      super.paintBorder(c, g, x, y, width, height)
      if (isHighlighted) {
        val g2d = g as Graphics2D
        g2d.color = editor.notebookAppearance.cellStripeSelectedColor()
        val lineY = y + borderHeight / 2
        g2d.fillRect(x, lineY - 1, width, 2)
      }
    }
  }
}