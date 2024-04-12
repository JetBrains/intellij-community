package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.notebooks.ui.jupyterToolbar.JupyterToolbar
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

class NotebookBelowLastCellPanelNew(val editor: EditorImpl) : JPanel(GridBagLayout()) {
  private var toolbar: JupyterToolbar? = null
  private val actionGroup = createActionGroup()

  init {
    if (editor.editorKind != EditorKind.DIFF) {
      isOpaque = false
      border = JBUI.Borders.empty(editor.notebookAppearance.CELL_BORDER_HEIGHT)
    }
  }

  fun initialize() {
    addComponentListeners()
    recreateToolbar()  // this toolbar is special - persistent and unique
  }

  private fun recreateToolbar() {
    actionGroup ?: return
    toolbar?.let { remove(it) }
    toolbar = JupyterToolbar(actionGroup, editor.contentComponent)
    add(toolbar)
    adjustToolbarBounds()
  }

  private fun addComponentListeners() {
    this.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        super.componentResized(e)
        adjustToolbarBounds()
      }

      override fun componentShown(e: ComponentEvent?) {
        super.componentShown(e)
        adjustToolbarBounds()
      }
    })
  }

  override fun updateUI() {
    super.updateUI()
    recreateToolbar()
  }

  private fun createActionGroup(): ActionGroup? {
    return CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as? ActionGroup
  }

  private fun adjustToolbarBounds() {
    toolbar?.let { tb ->
      val toolbarPreferredSize = tb.preferredSize
      val xOffset = (this.width - toolbarPreferredSize.width) / 2
      val editorComponent = editor.contentComponent
      val panelLocationInEditor = SwingUtilities.convertPoint(this, Point(0, 0), editorComponent)

      val xCoordinate = panelLocationInEditor.x + xOffset
      val yCoordinate = panelLocationInEditor.y

      tb.bounds = Rectangle(xCoordinate, yCoordinate, toolbarPreferredSize.width, toolbarPreferredSize.height)
      revalidate()
      repaint()
    }
  }

  companion object {
    const val ACTION_GROUP_ID = "Jupyter.BelowCellNewPanel"
  }
}
