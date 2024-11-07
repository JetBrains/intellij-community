package com.intellij.notebooks.ui.visualization

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notebooks.ui.jupyterToolbar.JupyterAboveCellToolbarService
import com.intellij.notebooks.ui.jupyterToolbar.JupyterAddNewCellToolbar
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.JBUI
import java.awt.GridBagLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

class NotebookBelowLastCellPanel(val editor: EditorImpl) : JPanel(GridBagLayout()) {
  private var toolbar: JupyterAddNewCellToolbar? = null

  init {
    if (editor.isOrdinaryNotebookEditor()) {
      isOpaque = false
      border = JBUI.Borders.empty(editor.notebookAppearance.cellBorderHeight)
      addComponentListeners()
      toolbar = JupyterAddNewCellToolbar(getActionGroup(), toolbarTargetComponent = this)
      add(toolbar)
      adjustToolbarBounds()
    }
  }

  private fun addComponentListeners() {
    this.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = adjustToolbarBounds()
    })
  }

  private fun getActionGroup(): ActionGroup =
    CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as ActionGroup

  private fun adjustToolbarBounds() {
    toolbar?.let { tb ->
      tb.bounds = JupyterAboveCellToolbarService.calculateToolbarBounds(editor, this, tb)
      revalidate()
      repaint()
    }
  }

  companion object {
    private const val ACTION_GROUP_ID = "Jupyter.BelowCellNewPanel"
  }
}