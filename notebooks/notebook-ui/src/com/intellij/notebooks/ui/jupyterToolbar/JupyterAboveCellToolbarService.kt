package com.intellij.notebooks.ui.jupyterToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.*
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.KeyAdapter
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

@Service(Service.Level.PROJECT)  // PY-66455 PY-72283
class JupyterAboveCellToolbarService(private val scope: CoroutineScope) : Disposable {
  private var currentEditor: Editor? = null
  private var currentPanel: JComponent? = null

  private val additionalToolbarEnabled = Registry.`is`("jupyter.cell.additional.toolbar")
  private var currentToolbar: JupyterAddNewCellToolbar? = null
  private var currentAdditionalToolbar: JupyterAdditionalToolbar? = null

  private val hideToolbarTimer = Timer(TOOLBAR_HIDE_DELAY) { conditionallyHideAllToolbars() }
  private val actionGroup: ActionGroup? = getActionGroup()
  private val additionalActionGroup: ActionGroup? = getAdditionalActionGroup()
  private var editorComponentListener: ComponentAdapter? = null
  private var editorKeyListener: KeyAdapter? = null
  private var showToolbarJob: Job? = null
  private var isMouseInsidePanel = false

  fun requestToolbarDisplay(panel: JComponent, editor: Editor) {
    showToolbarJob?.cancel()
    isMouseInsidePanel = true

    showToolbarJob = scope.launch(Dispatchers.Main) {
      delay(TOOLBAR_SHOW_DELAY)

      val mousePos = MouseInfo.getPointerInfo().location
      SwingUtilities.convertPointFromScreen(mousePos, panel)

      val panelWidth = panel.width
      val relativeMouseX = mousePos.x.toFloat() / panelWidth

      when (relativeMouseX) {
        in ADD_CELL_TOOLBAR_START_RATIO..ADD_CELL_TOOLBAR_END_RATIO -> {
          // showing central, add cell toolbar
          hideAdditionalToolbarUnconditionally()
          if (currentPanel != panel || currentToolbar == null) {
            hideToolbarUnconditionally()
            currentPanel = panel
            currentEditor = editor
            createAndShowToolbar(editor)
          }
        }
        in ADDITIONAL_TOOLBAR_START_RATIO..1.0 -> {
          if (!additionalToolbarEnabled) return@launch
          // showing additional toolbar
          hideToolbarUnconditionally()
          if (currentPanel != panel || currentAdditionalToolbar == null) {
            hideAdditionalToolbarUnconditionally()
            currentPanel = panel
            currentEditor = editor
            createAndShowAdditionalToolbar(editor)
          }
        }
      }
    }
  }

  fun requestToolbarHide() {
    isMouseInsidePanel = false
    hideToolbarTimer.restart()
    showToolbarJob?.cancel()
  }

  fun adjustAllToolbarsPositions() {
    adjustToolbarPosition()
    adjustAdditionalToolbarPosition()
  }

  fun hideAllToolbarsUnconditionally() {
    hideToolbarUnconditionally()
    hideAdditionalToolbarUnconditionally()
  }

  private fun createAndShowToolbar(editor: Editor) {
    actionGroup ?: return
    if (currentToolbar == null) {
      currentToolbar = JupyterAddNewCellToolbar(actionGroup, currentPanel!!)
    }
    editor.contentComponent.add(currentToolbar, 0)
    hideToolbarTimer.stop()
    adjustToolbarPosition()

    editor.contentComponent.revalidate()
    editor.contentComponent.repaint()
  }

  private fun createAndShowAdditionalToolbar(editor: Editor) {
    additionalActionGroup ?: return
    if (currentAdditionalToolbar == null) {
      currentAdditionalToolbar = JupyterAdditionalToolbar(additionalActionGroup, currentPanel!!)
    }
    editor.contentComponent.add(currentAdditionalToolbar, 0)
    hideToolbarTimer.stop()
    adjustAdditionalToolbarPosition()

    editor.contentComponent.revalidate()
    editor.contentComponent.repaint()
  }

  private fun conditionallyHideAllToolbars() {
    if (shouldHideAllToolbars()) {
      // Hide toolbars if the mouse is outside both
      hideAllToolbarsUnconditionally()
    }
    else {
      hideToolbarTimer.restart()
    }
  }

  private fun shouldHideAllToolbars(): Boolean {
    val mousePos = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(mousePos, currentEditor?.contentComponent ?: return true)

    val isMouseInAddToolbar = currentToolbar?.bounds?.contains(mousePos) == true
    val isMouseInAdditionalToolbar = currentAdditionalToolbar?.bounds?.contains(mousePos) == true

    return !(isMouseInAddToolbar || isMouseInAdditionalToolbar)
  }

  private fun hideToolbarUnconditionally() {
    currentToolbar?.let {
      currentEditor?.contentComponent?.remove(it)
      currentToolbar = null
    }
    forceUIUpdate()
  }

  private fun hideAdditionalToolbarUnconditionally() {
    currentAdditionalToolbar?.let {
      currentEditor?.contentComponent?.remove(it)
      currentAdditionalToolbar = null
    }
    forceUIUpdate()
  }

  private fun adjustToolbarPosition() {
    currentToolbar?.let { tb ->
      val e = currentEditor ?: return
      val p = currentPanel ?: return
      tb.bounds = calculateToolbarBounds(e, p, tb)
    }
  }

  private fun adjustAdditionalToolbarPosition() {
    currentAdditionalToolbar?.let { tb ->
      val e = currentEditor ?: return
      val p = currentPanel ?: return
      tb.bounds = calculateToolbarBoundsForAdditional(e, p, tb)
    }
  }

  private fun getActionGroup(): ActionGroup? = CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup
  private fun getAdditionalActionGroup(): ActionGroup? = CustomActionsSchema.getInstance().getCorrectedAction(ADDITIONAL_ACTION_GROUP_ID) as? ActionGroup

  override fun dispose() {
    showToolbarJob?.cancel()
    currentEditor?.contentComponent?.removeComponentListener(editorComponentListener)
    currentEditor?.contentComponent?.removeKeyListener(editorKeyListener)

    hideAllToolbarsUnconditionally()
  }

  private fun forceUIUpdate() {
    currentEditor?.contentComponent?.revalidate()
    currentEditor?.contentComponent?.repaint()
  }

  companion object {
    fun getInstance(project: Project): JupyterAboveCellToolbarService = project.getService(JupyterAboveCellToolbarService::class.java)

    private const val TOOLBAR_SHOW_DELAY = 250L
    private const val TOOLBAR_HIDE_DELAY = 600

    private const val ADD_CELL_TOOLBAR_X_OFFSET_RATIO = 0.5
    private const val ADDITIONAL_TOOLBAR_X_OFFSET_RATIO = 0.95

    private const val ADD_CELL_TOOLBAR_START_RATIO = 0.15
    private const val ADD_CELL_TOOLBAR_END_RATIO = 0.6
    private const val ADDITIONAL_TOOLBAR_START_RATIO = ADD_CELL_TOOLBAR_END_RATIO

    private const val ACTION_GROUP_ID = "Jupyter.AboveCellPanelNew"
    private const val ADDITIONAL_ACTION_GROUP_ID = "Jupyter.AboveCellAdditionalToolbar"
    private val DELIMITER_SIZE = DefaultNotebookEditorAppearanceSizes.distanceBetweenCells

    fun calculateToolbarBounds(
      editor: Editor,
      panel: JComponent,
      toolbar: JupyterAddNewCellToolbar,
    ): Rectangle {
      return calculateToolbarBoundsCommon(editor, panel, toolbar, ADD_CELL_TOOLBAR_X_OFFSET_RATIO)
    }

    fun calculateToolbarBoundsForAdditional(
      editor: Editor,
      panel: JComponent,
      toolbar: JupyterAdditionalToolbar,
    ): Rectangle {
      return calculateToolbarBoundsCommon(editor, panel, toolbar, ADDITIONAL_TOOLBAR_X_OFFSET_RATIO, true)
    }

    private fun calculateToolbarBoundsCommon(
      editor: Editor,
      panel: JComponent,
      toolbar: JPanel,
      horizontalOffsetRatio: Double,
      isAdditionalToolbar: Boolean = false,
    ): Rectangle {
      val panelHeight = panel.height
      val panelWidth = panel.width

      val toolbarHeight = toolbar.preferredSize.height
      val toolbarWidth = toolbar.preferredSize.width

      val panelRoofHeight = panelHeight - DELIMITER_SIZE

      val xOffset = (panelWidth * horizontalOffsetRatio - toolbarWidth / 2).toInt()
      val yOffset = when (isAdditionalToolbar) {
        true -> panelHeight - panelRoofHeight - (toolbarHeight / 2)
        else -> panelHeight - DELIMITER_SIZE - (toolbarHeight / 2)
      }
      val editorComponent = editor.contentComponent
      val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editorComponent)

      val xCoordinate = panelLocationInEditor.x + xOffset
      val yCoordinate = panelLocationInEditor.y + yOffset

      return Rectangle(xCoordinate, yCoordinate, toolbarWidth, toolbarHeight)
    }
  }
}