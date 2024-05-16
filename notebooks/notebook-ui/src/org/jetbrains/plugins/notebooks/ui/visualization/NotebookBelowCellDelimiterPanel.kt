package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

class NotebookBelowCellDelimiterPanel(
  val editor: EditorImpl,
  @Nls private val tooltipText: String?,
  private val executionCount: Int?,
  statusIcon: Icon,
  private val isExecutable: Boolean
) : JPanel(BorderLayout()) {
  private val notebookAppearance = editor.notebookAppearance
  // same as in the [org.jetbrains.plugins.notebooks.ui.visualization.NotebookAboveCellDelimiterPanelNew]
  private val standardDelimiterHeight = editor.notebookAppearance.CELL_BORDER_HEIGHT / 4
  private var isCollapsed = getCollapsed()

  init {
    setPanelBackground()
    setBorder()

    if (!editor.editorKind.isDiff() && isExecutable && !isCollapsed) {
      val executionCountText = executionCount?.let { if (it > 0) "[$it]" else "" } ?: ""
      val executionLabel = JLabel(executionCountText)

      executionLabel.icon = statusIcon
      executionLabel.font = EditorUtil.getEditorFont()
      executionLabel.foreground = UIUtil.getLabelInfoForeground()
      tooltipText?.let { executionLabel.toolTipText = it }
      add(executionLabel, BorderLayout.WEST)
    }
  }

  private fun setPanelBackground() {
    background = when (isExecutable) {
      true -> notebookAppearance.getCodeCellBackground(editor.colorsScheme) ?: editor.colorsScheme.defaultBackground
      false -> editor.colorsScheme.defaultBackground
    }
  }


  private fun setBorder() {
    val dimension = when (editor.editorKind.isDiff()) {
      true -> Dimension(preferredSize.width, getJupyterCellSpacing(editor))
      false -> Dimension(preferredSize.width, standardDelimiterHeight)
    }
    border = BorderFactory.createEmptyBorder(dimension.height, 0, dimension.height, 0)
  }

  private fun getCollapsed(): Boolean = !isExecutionCountDefined() && tooltipText == null

  private fun isExecutionCountDefined(): Boolean = executionCount?.let { it > 0 } ?: false

  @Suppress("USELESS_ELVIS")
  override fun updateUI() {
    // This method is called within constructor of JPanel, at this time state is not yet initialized, reference is null.
    editor ?: return
    setPanelBackground()
    super.updateUI()
  }
}
