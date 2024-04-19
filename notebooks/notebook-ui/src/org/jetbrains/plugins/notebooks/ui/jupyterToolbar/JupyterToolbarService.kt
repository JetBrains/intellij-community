package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.notebooks.ui.JupyterUiCoroutine
import org.jetbrains.plugins.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookAboveCellDelimiterPanelNew
import java.awt.Canvas
import java.awt.Font
import java.awt.MouseInfo
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.coroutines.resume


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
  private val coroutineScope = JupyterUiCoroutine.Utils.edtScope.childScope()
  private var displayJob: Job? = null

  private val hideToolbarTimer = Timer(TOOLBAR_HIDE_DELAY) { hideToolbarConditionally() }
  private val actionGroup: ActionGroup? = createActionGroup()

  private var actualToolbarWidth: Int? = null
  private var actualToolbarHeight: Int? = null

  @RequiresEdt
  fun requestToolbarDisplay(panel: JPanel, editor: EditorImpl) {
    val shouldDisplayToolbar = currentPanel != panel || currentToolbarPopup == null || currentToolbarPopup?.isDisposed == true
    displayJob?.cancel()

    displayJob = coroutineScope.launch {
      delay(TOOLBAR_SHOW_DELAY)
      if (isActive) {
        if (shouldDisplayToolbar) {
          hideToolbarUnconditionally()
          currentPanel = panel
          createAndShowToolbar(panel, editor)
        }
      }
    }
  }

  @RequiresEdt
  fun requestToolbarHide() {
    displayJob?.cancel() // Cancel the display job when hiding
    hideToolbarTimer.restart()
  }

  private suspend fun createUpdatedJupyterToolbar(actionGroup: ActionGroup, targetComponent: JComponent): JupyterToolbar {
    return suspendCancellableCoroutine { continuation ->
      JupyterToolbar.createImmediatelyUpdatedJupyterToolbar(actionGroup, targetComponent) {
        if (!continuation.isCompleted) {
          continuation.resume(it)
        }
      }
    }
  }

  private suspend fun createAndShowToolbar(panel: JPanel, editor: EditorImpl) {
    actionGroup ?: return

    if (!actualSizesKnown()) { calculateActionsWidth() }
    calculateApproximateActionWidth(actionGroup, JBFont.small())
    currentToolbar?.let { currentToolbar = null }
    currentToolbar = createUpdatedJupyterToolbar(actionGroup, editor.contentComponent).also {
      it.updateActionsImmediately()
    }

    val component = BorderLayoutPanel().apply {
      addToCenter(currentToolbar!!.component)
      border = null
    }

    currentToolbarPopup?.let { destroyPopup() }
    currentToolbarPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(component, panel)
      .setCancelOnClickOutside(true)
      .setCancelKeyEnabled(true)
      .setShowBorder(false)
      .setShowShadow(false)
      .createPopup()

    adjustToolbarPosition()
    hideToolbarTimer.stop()
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

      currentToolbarPopup?.content?.let{ ct ->
        SwingUtilities.convertPointFromScreen(mousePos, ct)

        if (currentToolbar?.bounds?.contains(mousePos) == true) {
          hideToolbarTimer.restart()
          return
        } else {
          hideToolbarUnconditionally()
        }
      }
    } catch (e: IllegalStateException) { hideToolbarUnconditionally() }
  }

  private fun calculateApproximateActionWidth(actionGroup: ActionGroup, font: Font?): Int {
    val metrics = Canvas().getFontMetrics(font)
    var width = 0

    actionGroup.getChildren(null).forEach { action ->
      when (action) {
        is ActionGroup -> width += calculateApproximateActionWidth(action, font)
        is Separator -> width += SEPARATOR_WIDTH
        is AnAction -> {
          val text = action.templatePresentation.text
          val icon = action.templatePresentation.icon

          val textWidth = text?.let { metrics.stringWidth(it) } ?: SEPARATOR_WIDTH
          val iconWidth = icon?.iconWidth ?: 0
          val spacing = if (text != null && icon != null) BUTTON_SPACERS_SUM else 0

          val elementWidth = textWidth + iconWidth + spacing
          width += elementWidth
        }
      }
    }

    return width
  }

  @RequiresEdt
  fun hideToolbarUnconditionally() {
    currentToolbar = null
    currentPanel = null
    destroyPopup()
  }

  private fun destroyPopup() {
    currentToolbarPopup?.let { p ->
      p.cancel()
      if (!p.isDisposed) { Disposer.dispose(p) }
      currentToolbarPopup = null
    }
  }

  @RequiresEdt
  fun adjustToolbarPosition() {
    val toolbarPopup = currentToolbarPopup ?: return
    val panel = currentPanel ?: return

    calculatePopupLocation()?.let {
      val relPoint = RelativePoint(panel, it)
      when (toolbarPopup.isVisible) {
        true -> toolbarPopup.setLocation(relPoint.screenPoint)
        false -> toolbarPopup.show(relPoint)
      }
    } ?: run {
      hideToolbarUnconditionally()
      return
    }
  }

  private fun calculatePopupLocation(): Point? {
    val panelHeight = currentPanel?.height ?: return null
    val panelWidth = currentPanel?.width ?: return null
    actualToolbarWidth ?: return null
    actualToolbarHeight ?: return null

    val xOffset = (panelWidth - actualToolbarWidth!!) / 2
    val yOffset = (panelHeight - (1.5 * DELIMITER_SIZE) - (actualToolbarHeight!! / 2)).toInt()
    val result = Point(xOffset, yOffset)
    return result
  }

  fun setActualToolbarSize(width: Int, height: Int) {
    actualToolbarWidth = width
    actualToolbarHeight = height
  }

  private fun actualSizesKnown() = actualToolbarWidth != null && actualToolbarHeight != null

  private fun calculateActionsWidth() {
    actionGroup ?: return
    actualToolbarWidth = calculateApproximateActionWidth(actionGroup, JBFont.small()) + TOOLBAR_BORDER_SIZE
    actualToolbarHeight = TOOLBAR_TOTAL_HEIGHT
  }

  private fun createActionGroup(): ActionGroup? = CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup

  companion object {
    private const val ACTION_GROUP_ID = "Jupyter.AboveCellPanelNew"
    private const val TOOLBAR_HIDE_DELAY = 150
    private const val TOOLBAR_SHOW_DELAY: Long = 30

    private val DELIMITER_SIZE = DefaultNotebookEditorAppearanceSizes.CELL_BORDER_HEIGHT / 2

    private val SEPARATOR_WIDTH = JBUI.scale(7)  // 1px + 3 + 3 borders
    private val BUTTON_SPACERS_SUM = JBUI.scale(14)
    private val TOOLBAR_BORDER_SIZE = JBUI.scale(8)
    private val TOOLBAR_TOTAL_HEIGHT = JBUI.scale(32)

    fun getInstance(project: Project): JupyterToolbarService = project.getService(JupyterToolbarService::class.java)
  }
}
