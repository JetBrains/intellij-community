package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

/**
 * Adds listeners for "above cell" panels to trigger toolbar-related functions in the [JupyterToolbarService].
 */
class JupyterToolbarPanelListeners(  // PY-66455
  private val panel: JPanel,
  project: Project,
  private val editor: EditorImpl
) : MouseAdapter(), ComponentListener {

  private val toolbarService = JupyterToolbarService.getInstance(project)

  init {
    panel.addMouseListener(this)
    panel.addComponentListener(this)
  }

  override fun mouseEntered(e: MouseEvent?) {
    toolbarService.requestToolbarDisplay(panel, editor)
  }

  override fun mouseExited(e: MouseEvent?) {
    toolbarService.requestToolbarHide()
  }

  override fun mouseClicked(e: MouseEvent?) {
    toolbarService.hideToolbarUnconditionally()
  }

  override fun componentResized(e: ComponentEvent?) {
    toolbarService.adjustToolbarPosition()
  }

  override fun componentMoved(e: ComponentEvent?) {
    toolbarService.adjustToolbarPosition()
  }

  override fun componentShown(e: ComponentEvent?) {
    toolbarService.hideToolbarUnconditionally()
  }
  override fun componentHidden(e: ComponentEvent?) {
    toolbarService.hideToolbarUnconditionally()
  }
}
