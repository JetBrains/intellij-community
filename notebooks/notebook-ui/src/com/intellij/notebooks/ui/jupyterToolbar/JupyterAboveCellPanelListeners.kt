package com.intellij.notebooks.ui.jupyterToolbar

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import java.awt.event.*
import javax.swing.JPanel

/**
 * Adds listeners for "above cell" panels to trigger toolbar-related functions in the [JupyterAboveCellToolbarService].
 */
class JupyterAboveCellPanelListeners(  // PY-66455
  private val panel: JPanel,
  project: Project,
  private val editor: EditorImpl
): Disposable  {
  private val toolbarService = JupyterAboveCellToolbarService.getInstance(project)
  private var panelMouseListener: MouseAdapter? = null
  private var panelComponentListener: ComponentAdapter? = null

  init {
    addPanelMouseListener()
    addPanelComponentListener()
  }

  private fun addPanelMouseListener() {
    panelMouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) = toolbarService.requestToolbarDisplay(panel, editor)
      override fun mouseMoved(e: MouseEvent) = toolbarService.requestToolbarDisplay(panel, editor)
      override fun mouseExited(e: MouseEvent) = toolbarService.requestToolbarHide()
      override fun mouseClicked(e: MouseEvent?) = toolbarService.hideAllToolbarsUnconditionally()
    }

    panel.addMouseListener(panelMouseListener)
    panel.addMouseMotionListener(panelMouseListener)
  }

  private fun addPanelComponentListener() {
    panelComponentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = toolbarService.adjustAllToolbarsPositions()
      override fun componentMoved(e: ComponentEvent?) = toolbarService.adjustAllToolbarsPositions()
      //override fun componentShown(e: ComponentEvent?) = toolbarService.hideToolbarUnconditionally()
      //override fun componentHidden(e: ComponentEvent?) = toolbarService.hideToolbarUnconditionally()
    }

    panel.addComponentListener(panelComponentListener)
  }


  override fun dispose() {
    panel.removeMouseListener(panelMouseListener)
    panel.removeComponentListener(panelComponentListener)
  }

}
