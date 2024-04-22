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

  private enum class PanelRegion { MIDDLE, RIGHT, NONE }

  init {
    panel.addMouseListener(this)
    panel.addComponentListener(this)
  }

  override fun mouseEntered(e: MouseEvent?) {
    e?.let {
      val enteredPart = determineMouseRegion(it.x)
      when (enteredPart) {
        PanelRegion.MIDDLE -> toolbarService.requestToolbarDisplay(panel, editor)
        // todo: for additional cell actions toolbar, also implement mouse moved
        // https://www.figma.com/file/ApfhZCnjLV7NRlksW6hy4Y/Jupyter?type=design&node-id=239-8996&mode=design&t=HhbICTruXlSWMcQh-0
        PanelRegion.RIGHT -> return
        PanelRegion.NONE -> return
      }
    }
  }

  private fun determineMouseRegion(xPos: Int): PanelRegion {
    val panelWidth = panel.width
    val middleStart = (panelWidth * 0.25).toInt()
    val middleEnd = (panelWidth * 0.66).toInt()
    val rightStart = (panelWidth * 0.75).toInt()

    return when (xPos) {
      in middleStart until middleEnd -> PanelRegion.MIDDLE
      in rightStart until panelWidth -> PanelRegion.RIGHT
      else -> PanelRegion.NONE
    }
  }

  override fun mouseExited(e: MouseEvent?) = toolbarService.requestToolbarHide()
  override fun mouseClicked(e: MouseEvent?) = toolbarService.hideToolbarUnconditionally()
  override fun componentShown(e: ComponentEvent?) = toolbarService.hideToolbarUnconditionally()
  override fun componentHidden(e: ComponentEvent?) = toolbarService.hideToolbarUnconditionally()
  override fun componentResized(e: ComponentEvent?) = toolbarService.adjustToolbarPosition()
  override fun componentMoved(e: ComponentEvent?) = toolbarService.adjustToolbarPosition()
}
