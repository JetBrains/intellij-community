package com.intellij.notebooks.ui.visualization

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.JBUI
import com.intellij.notebooks.ui.jupyterToolbar.JupyterAboveCellToolbarService
import com.intellij.notebooks.ui.jupyterToolbar.JupyterAddNewCellToolbar
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import java.awt.GridBagLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

class NotebookBelowLastCellPanel(val editor: EditorImpl) : JPanel(GridBagLayout()) {
  private var toolbar: JupyterAddNewCellToolbar? = null
  private val actionGroup = createActionGroup()

  init {
    if (editor.isOrdinaryNotebookEditor()) {
      isOpaque = false
      border = JBUI.Borders.empty(editor.notebookAppearance.cellBorderHeight)
      addComponentListeners()
      recreateToolbar()
    }
  }

  private fun recreateToolbar() {
    actionGroup ?: return
    toolbar?.let { remove(it) }
    toolbar = JupyterAddNewCellToolbar(actionGroup, editor.contentComponent)
    add(toolbar)
    adjustToolbarBounds()
  }

  private fun addComponentListeners() {
    this.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        super.componentResized(e)
        adjustToolbarBounds()
      }

      override fun componentShown(e: ComponentEvent?) {
        super.componentShown(e)
        adjustToolbarBounds()
      }
    })
  }

  override fun updateUI() {
    super.updateUI()
    recreateToolbar()
  }

  private fun createActionGroup(): ActionGroup? =
    CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup

  private fun adjustToolbarBounds() {
    toolbar?.let { tb ->
      tb.bounds = JupyterAboveCellToolbarService.calculateToolbarBounds(editor, this, tb)
      revalidate()
      repaint()
    }
  }

  companion object {
    const val ACTION_GROUP_ID = "Jupyter.BelowCellNewPanel"
  }
}
