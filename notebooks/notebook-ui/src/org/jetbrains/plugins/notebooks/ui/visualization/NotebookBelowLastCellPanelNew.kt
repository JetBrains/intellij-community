package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.plugins.notebooks.ui.jupyterToolbar.JupyterToolbar
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

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
    toolbar = JupyterToolbar(actionGroup, editor.contentComponent)
    add(toolbar)
    adjustToolbarBounds()
    addComponentListeners()
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
      val yCoordinate = panelLocationInEditor.y + Y_OFFSET

      tb.bounds = Rectangle(xCoordinate, yCoordinate, toolbarPreferredSize.width, toolbarPreferredSize.height)
      revalidate()
      repaint()
    }
  }

  companion object {
    private val Y_OFFSET = JBUIScale.scale(30)
    const val ACTION_GROUP_ID = "Jupyter.BelowCellNewPanel"
  }
}