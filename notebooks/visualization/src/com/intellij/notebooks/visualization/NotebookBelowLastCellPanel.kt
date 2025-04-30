// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightable
import com.intellij.notebooks.visualization.ui.jupyterToolbars.JupyterAddNewCellToolbar
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.JBEmptyBorder
import org.intellij.lang.annotations.Language
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

/**
 * Basically, this panel consists only of
 * * an "add new cell" toolbar
 * * a highlightable border to show drop destination
 */
class NotebookBelowLastCellPanel(
  val editor: EditorImpl,
) : JPanel(FlowLayout(FlowLayout.CENTER)), DropHighlightable {

  private var isHighlighted = false

  init {
    if (editor.isOrdinaryNotebookEditor()) {
      isOpaque = false
      border = HighlightableTopBorder(editor.notebookAppearance.cellBorderHeight)
      val actionGroup = ActionManager.getInstance().getAction(ACTION_GROUP_ID) as ActionGroup
      add(JupyterAddNewCellToolbar(actionGroup, toolbarTargetComponent = this))
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
    override fun paintBorder(c: java.awt.Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
      super.paintBorder(c, g, x, y, width, height)
      if (isHighlighted) {
        val g2d = g as Graphics2D
        g2d.color = editor.notebookAppearance.cellStripeSelectedColor.get()
        val lineY = y + borderHeight / 2
        g2d.fillRect(x, lineY - 1, width, 2)
      }
    }
  }

  companion object {
    @Language("devkit-action-id")
    private const val ACTION_GROUP_ID = "Jupyter.CreateNewCellsPanel"
  }
}