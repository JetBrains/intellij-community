package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.*
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class JupyterToolbarManager(
  private val editor: EditorImpl,
  private val panel: JPanel,
  private val actionGroupId: String,
  private val firstLine: Int = 0
) {  // See PY-66455
  private var toolbar: JupyterToolbar? = null
  private var hideToolbarTimer = Timer(TOOLBAR_HIDE_DELAY) { conditionallyHideToolBar() }

  init {
    initPanelMouseListeners()
    addPanelComponentListener()
    addEditorKeyListener()
    addEditorComponentListener()
  }

  private fun initPanelMouseListeners() {
    val mouseAdapter = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        hideToolbarTimer.stop()
        showToolbar()
      }

      override fun mouseExited(e: MouseEvent) {
        hideToolbarTimer.restart()
      }
    }

    panel.addMouseListener(mouseAdapter)
  }

  private fun addPanelComponentListener() {
    val componentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        super.componentResized(e)
        toolbar?.let {
          it.bounds  = calculateToolbarBounds(editor, panel, it.preferredSize)
          panel.revalidate()
          panel.repaint()
        }
      }

      override fun componentMoved(e: ComponentEvent?) {
        super.componentMoved(e)
        toolbar?.let {
          it.bounds  = calculateToolbarBounds(editor, panel, it.preferredSize)
          panel.revalidate()
          panel.repaint()
        }
      }
    }
    panel.addComponentListener(componentListener)
  }

  private fun addEditorKeyListener() {
    val keyAdapter = object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent) = hideToolBar()
      override fun keyPressed(e: KeyEvent) = hideToolBar()
    }

    editor.contentComponent.addKeyListener(keyAdapter)
  }

  private fun addEditorComponentListener() {
    editor.contentComponent.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = hideToolBar()
    })
  }

  /**
   * Checks if the mouse is over the toolbar before hiding it.
   * This is necessary because mouseExited events on the panel can trigger even when the mouse
   * is over the toolbar due to its position in the Z-stack.
   * If the mouse is indeed over the toolbar, hiding is deferred to prevent an unintended disappearance,
   * acting as an additional safeguard for better user experience.
   */
  private fun conditionallyHideToolBar() {
    toolbar?.let { tb ->
      val mousePos = MouseInfo.getPointerInfo().location
      SwingUtilities.convertPointFromScreen(mousePos, editor.contentComponent)

      if (tb.bounds.contains(mousePos)) {
        hideToolbarTimer.restart()
      } else {
        hideToolBar()  // mouse is not over the toolbar - we may hide it
      }
    }
  }

  fun hideToolBar() {
    toolbar?.let {
      editor.contentComponent.remove(it)
      editor.contentComponent.revalidate()
      editor.contentComponent.repaint()
      toolbar = null
      JupyterToolbarVisibilityManager.notifyToolbarHidden(this)
    }
  }

  private fun showToolbar() {
    if (toolbar == null) {
      val actionGroup = createActionGroup() ?: return
      toolbar = JupyterToolbar(actionGroup, firstLine).apply {
        targetComponent = editor.contentComponent
      }
    }
    JupyterToolbarVisibilityManager.requestToolbarDisplay(this)
    editor.contentComponent.add(toolbar, 0)
    positionToolbar()
  }

  private fun positionToolbar() {
    toolbar?.let { tb ->
      val bounds = calculateToolbarBounds(editor, panel, tb.preferredSize)
      tb.bounds = bounds
    }
  }

  private fun createActionGroup(): ActionGroup? {
    return CustomActionsSchema.getInstance().getCorrectedAction(actionGroupId) as? ActionGroup
  }

  companion object {
    private val DEFAULT_Y_OFFSET = JBUIScale.scale(-10)
    private const val TOOLBAR_HIDE_DELAY = 800

    fun calculateToolbarBounds(
      editor: EditorImpl,
      panel: JPanel,
      toolbar: Dimension,
      extraYOffset: Int = 0
    ) : Rectangle {
      val xOffset = (panel.width - toolbar.width) / 2
      val yOffset = DEFAULT_Y_OFFSET + extraYOffset

      val editorComponent = editor.contentComponent
      val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editorComponent)

      val xCoordinate = panelLocationInEditor.x + xOffset
      val yCoordinate = panelLocationInEditor.y + yOffset
      return Rectangle(xCoordinate, yCoordinate, toolbar.width, toolbar.height)
    }
  }
}