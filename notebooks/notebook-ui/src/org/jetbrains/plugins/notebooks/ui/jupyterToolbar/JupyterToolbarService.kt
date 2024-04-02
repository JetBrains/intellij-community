package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookAboveCellDelimiterPanelNew
import java.awt.MouseInfo
import java.awt.Point
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer


/**
 * This class encapsulates a service that manages the display toggle action of a [JupyterToolbar] among multiple cells.
 *
 * The following rules apply:
 * - The toolbar is always positioned beneath the last cell (not managed by this service)
 * - Only one additional toolbar is allowed among cells (managed by this service)
 *
 * When a cursor hovers over a [NotebookAboveCellDelimiterPanelNew], a toolbar will appear.
 * If a toolbar is already displayed above another panel, this older instance will be hidden.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class JupyterToolbarService {  // PY-66455
  private var currentPanel: JPanel? = null
  private var currentToolbar: JupyterToolbar? = null
  private var currentToolbarPopup: JBPopup? = null

  private val hideToolbarTimer = Timer(TOOLBAR_HIDE_DELAY) { hideToolbarConditionally() }
  private val actionGroup: ActionGroup? = createActionGroup()

  fun requestToolbarDisplay(panel: JPanel, editor: EditorImpl) {
    val shouldDisplayToolbar = currentPanel != panel || currentToolbarPopup == null || currentToolbarPopup?.isDisposed == true

    if (shouldDisplayToolbar) {
      hideToolbarUnconditionally()
      currentPanel = panel
      createAndShowToolbar(panel, editor)
    }
  }

  fun requestToolbarHide() = hideToolbarTimer.restart()

  private fun createAndShowToolbar(panel: JPanel, editor: EditorImpl) {
    actionGroup ?: return

    if (currentToolbar == null) {
      currentToolbar = JupyterToolbar(actionGroup, editor.contentComponent)
    }

    if (currentToolbarPopup == null) {
      currentToolbarPopup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(currentToolbar!!.component, panel)
        .setCancelOnClickOutside(true)
        .setCancelKeyEnabled(true)
        .setShowBorder(false)
        .setShowShadow(false)
        .createPopup().also {
          val point = calculatePopupLocation() ?: return
          it.show(RelativePoint(panel, point))
        }
    }
    hideToolbarTimer.stop()
    adjustToolbarPosition()
  }

  /**
   * Checks if the mouse is over the toolbar before hiding it.
   * This is necessary because mouseExited events on the panel can trigger even when the mouse
   * is over the toolbar due to its position in the Z-stack.
   * If the mouse is indeed over the toolbar, hiding is deferred to prevent an unintended disappearance,
   * acting as an additional safeguard for better user experience.
   */
  private fun hideToolbarConditionally() {
    if (currentToolbar == null || currentToolbarPopup == null || currentToolbarPopup!!.isDisposed) return

    try {
      val mousePos = MouseInfo.getPointerInfo().location
      val content = currentToolbarPopup?.content
      if (content != null) {
        SwingUtilities.convertPointFromScreen(mousePos, content)

        if (currentToolbar?.bounds?.contains(mousePos) == true) {
          hideToolbarTimer.restart()
          return
        } else {
          hideToolbarUnconditionally()
        }
      }
    } catch (e: IllegalStateException) { hideToolbarUnconditionally() }
  }

  fun hideToolbarUnconditionally() {
    currentToolbar = null
    currentPanel = null
    currentToolbarPopup?.let { p ->
      p.cancel()
      if (!p.isDisposed) { Disposer.dispose(p) }
      currentToolbarPopup = null
    }
  }

  fun adjustToolbarPosition() {
    val toolbarPopup = currentToolbarPopup ?: return
    val panel = currentPanel ?: return
    // I had to use this method to correctly evaluate the toolbar's width
    currentToolbar?.updateActionsImmediately() ?: return

    calculatePopupLocation()?.let {
      val relPoint = RelativePoint(panel, it)
      toolbarPopup.setLocation(relPoint.screenPoint)
    } ?: run {
      hideToolbarUnconditionally()
      return
    }
  }

  private fun calculatePopupLocation(): Point? {
    val panelHeight = currentPanel?.height ?: return null
    val panelWidth = currentPanel?.width ?: return null
    val toolbarHeight = currentToolbar?.height ?: return null
    val toolbarWidth = currentToolbar?.width ?: return null

    val xOffset = (panelWidth - toolbarWidth) / 2
    val yOffset = (panelHeight - (1.5 * DELIMITER_SIZE) - (toolbarHeight / 2)).toInt()
    val result = Point(xOffset, yOffset)
    return result
  }

  private fun createActionGroup(): ActionGroup? = CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup

  companion object {
    private const val ACTION_GROUP_ID = "Jupyter.AboveCellPanelNew"
    private const val TOOLBAR_HIDE_DELAY = 600
    private val DELIMITER_SIZE = DefaultNotebookEditorAppearanceSizes.CELL_BORDER_HEIGHT / 2
    fun getInstance(project: Project): JupyterToolbarService = project.getService(JupyterToolbarService::class.java)
  }
}
