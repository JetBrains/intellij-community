package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.plugins.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

@Service(Service.Level.PROJECT)  // PY-66455
class JupyterAddCellToolbarService: Disposable {
  private var currentEditor: Editor? = null
  private var currentPanel: JPanel? = null
  private var currentToolbar: JupyterAddNewCellToolbar? = null

  private val hideToolbarTimer = Timer(TOOLBAR_HIDE_DELAY) { conditionallyHideToolBar() }
  private val actionGroup: ActionGroup? = createActionGroup()
  private var editorComponentListener: ComponentAdapter? = null
  private var editorKeyListener: KeyAdapter? = null

  fun requestToolbarDisplay(panel: JPanel, editor: Editor) {
    val shouldDisplayToolbar = currentPanel != panel || currentToolbar == null
    if (!shouldDisplayToolbar) return
    hideToolbarUnconditionally()
    currentPanel = panel
    updateCurrentEditor(editor)
    createAndShowToolbar(editor)
  }

  fun requestToolbarHide() {
    if (currentToolbar == null) return
    hideToolbarTimer.restart()
  }

  fun hideToolbarUnconditionally() {
    currentPanel = null
    currentToolbar?.let {
      currentEditor?.contentComponent?.remove(it)
      currentToolbar = null
    }
  }

  fun adjustToolbarPosition() {
    currentToolbar?.let { tb ->
      val e = currentEditor ?: return
      val p = currentPanel ?: return
      tb.bounds = calculateToolbarBounds(e, p, tb)
    }
  }

  private fun updateCurrentEditor(newEditor: Editor) {
    val prevEditor = currentEditor
    if (prevEditor == newEditor) return
    currentEditor = newEditor
    prevEditor?.contentComponent?.removeComponentListener(editorComponentListener)
    prevEditor?.contentComponent?.removeKeyListener(editorKeyListener)
    addEditorComponentListener()
    addEditorKeyListener()
  }

  private fun conditionallyHideToolBar() {
    currentToolbar?.let { tb ->
      val mousePos = MouseInfo.getPointerInfo().location
      SwingUtilities.convertPointFromScreen(mousePos, currentEditor!!.contentComponent)

      when (tb.bounds.contains(mousePos)) {
        true -> hideToolbarTimer.restart()
        else -> hideToolbarUnconditionally()
      }
    }
  }

  private fun createAndShowToolbar(editor: Editor) {
    actionGroup ?: return
    if (currentToolbar == null) currentToolbar = JupyterAddNewCellToolbar(actionGroup, editor.contentComponent)
    editor.contentComponent.add(currentToolbar, 0)
    hideToolbarTimer.stop()
    adjustToolbarPosition()

    currentToolbar?.updateActionsImmediately()
    editor.contentComponent.revalidate()
    editor.contentComponent.repaint()
  }

  private fun createActionGroup(): ActionGroup? = CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup

  private fun addEditorComponentListener() {
    editorComponentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = hideToolbarUnconditionally()
    }
    currentEditor?.contentComponent?.addComponentListener(editorComponentListener)
  }

  private fun addEditorKeyListener() {
    editorKeyListener = object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent) = hideToolbarUnconditionally()
      override fun keyPressed(e: KeyEvent) = hideToolbarUnconditionally()
    }

    currentEditor?.contentComponent?.addKeyListener(editorKeyListener)
  }

  override fun dispose() {
    currentEditor?.contentComponent?.removeComponentListener(editorComponentListener)
    currentEditor?.contentComponent?.removeKeyListener(editorKeyListener)

    currentToolbar?.let { tb ->
      currentEditor?.contentComponent?.remove(tb)
      currentToolbar = null
    }
  }

  companion object {
    fun getInstance(project: Project): JupyterAddCellToolbarService = project.getService(JupyterAddCellToolbarService::class.java)
    private const val ACTION_GROUP_ID = "Jupyter.AboveCellPanelNew"
    private const val TOOLBAR_HIDE_DELAY = 600
    private val DELIMITER_SIZE = DefaultNotebookEditorAppearanceSizes.distanceBetweenCells

    fun calculateToolbarBounds(
      editor: Editor,
      panel: JPanel,
      toolbar: JupyterAddNewCellToolbar,
    ) : Rectangle {
      val panelHeight = panel.height
      val panelWidth = panel.width

      val toolbarHeight = toolbar.preferredSize.height
      val toolbarWidth = toolbar.preferredSize.width

      val xOffset = (panelWidth - toolbarWidth) / 2
      val yOffset = (panelHeight - DELIMITER_SIZE - (toolbarHeight / 2))

      val editorComponent = editor.contentComponent
      val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editorComponent)

      val xCoordinate = panelLocationInEditor.x + xOffset
      val yCoordinate = panelLocationInEditor.y + yOffset
      return Rectangle(xCoordinate, yCoordinate, toolbarWidth, toolbarHeight)
    }
  }

}