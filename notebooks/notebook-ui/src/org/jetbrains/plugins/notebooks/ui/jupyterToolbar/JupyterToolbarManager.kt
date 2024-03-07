package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class JupyterToolbarManager(
  private val editor: EditorImpl,
  private val panel: JPanel,
  private val actionGroupId: String
) {  // PY-66455
  private var toolbar: JupyterToolbar? = null
  private var hideToolbarTimer = Timer(5000) { hideToolBar() }
  private var mouseEnteredTbFlag = false

  init {
    initPanelMouseListeners()
    addPanelComponentListener()
  }

  private fun initPanelMouseListeners() {
    val mouseAdapter = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        hideToolbarTimer.stop()
        showToolbar()
      }

      override fun mouseExited(e: MouseEvent) {
        if (!mouseEnteredTbFlag) {
          hideToolbarTimer.restart()
        }
      }
    }

    panel.addMouseListener(mouseAdapter)
  }

  private fun addToolbarListeners() {
    val toolbarListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        mouseEnteredTbFlag = true
        hideToolbarTimer.stop()
      }

      override fun mouseExited(e: MouseEvent) {
        mouseEnteredTbFlag = false
        hideToolbarTimer.restart()
      }
    }

    toolbar?.addMouseListener(toolbarListener)
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

  private fun showToolbar() {
    if (toolbar == null) {
      val actionGroup = createActionGroup() ?: return
      toolbar = JupyterToolbar(actionGroup).apply {
        targetComponent = editor.contentComponent
      }
    }
    JupyterToolbarVisibilityManager.requestToolbarDisplay(this)
    editor.contentComponent.add(toolbar, 0)
    positionToolbar()
    addToolbarListeners()
  }

  fun hideToolBar() {
    toolbar?.let {
      editor.contentComponent.remove(it)
      editor.contentComponent.revalidate()
      editor.contentComponent.repaint()
    }
    toolbar = null
    JupyterToolbarVisibilityManager.notifyToolbarHidden(this)
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
    fun calculateToolbarBounds(
      editor: EditorImpl,
      panel: JPanel,
      toolbar: Dimension,
      extraYOffset: Int = 0
    ) : Rectangle {
      val xOffset = (panel.width - toolbar.width) / 2
      val yOffset = JBUIScale.scale(-14) + extraYOffset

      val editorComponent = editor.contentComponent
      val panelLocationInEditor = SwingUtilities.convertPoint(panel, Point(0, 0), editorComponent)

      val xCoordinate = panelLocationInEditor.x + xOffset
      val yCoordinate = panelLocationInEditor.y + yOffset
      return Rectangle(xCoordinate, yCoordinate, toolbar.width, toolbar.height)
    }
  }
}