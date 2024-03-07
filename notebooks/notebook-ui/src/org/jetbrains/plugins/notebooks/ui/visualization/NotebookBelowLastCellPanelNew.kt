package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.notebooks.ui.jupyterToolbar.JupyterToolbar
import org.jetbrains.plugins.notebooks.ui.jupyterToolbar.JupyterToolbarManager
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel

class NotebookBelowLastCellPanelNew(val editor: EditorImpl) : JPanel(GridBagLayout()) {
  private var toolbar: JupyterToolbar? = null

  init {
    if (editor.editorKind != EditorKind.DIFF) {
      isOpaque = false
      preferredSize = Dimension(1, editor.notebookAppearance.CELL_BORDER_HEIGHT * 4)
    }
  }

  fun initialize() {
    // this toolbar is special - persistent and unique
    val actionGroup = createActionGroup() ?: return
    toolbar = JupyterToolbar(actionGroup).apply {
      targetComponent = editor.contentComponent
    }
    add(toolbar)

    toolbar?.let {
      it.bounds = calculateToolbarBounds()
      editor.contentComponent.revalidate()
      editor.contentComponent.repaint()
    }

    addComponentListeners()
  }

  private fun addComponentListeners() {
    this.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        super.componentResized(e)
        toolbar?.let {
          it.bounds = calculateToolbarBounds()
          revalidate()
          repaint()
        }
      }

      override fun componentShown(e: ComponentEvent?) {
        super.componentShown(e)
        toolbar?.let {
          it.bounds = calculateToolbarBounds()
          revalidate()
          repaint()
        }
      }
    })
  }

  private fun calculateToolbarBounds(): Rectangle {
    val toolbarPreferredSize = toolbar?.preferredSize ?: Dimension(0, 0)
    val newBounds = JupyterToolbarManager.calculateToolbarBounds(editor,
                                                                 this,
                                                                 toolbarPreferredSize,
                                                                 extraYOffset = 30)
    return newBounds
  }

  private fun createActionGroup(): ActionGroup? {
    return CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup
  }

  companion object {
    const val ACTION_GROUP_ID = "Jupyter.BelowCellNewPanel"
  }
}