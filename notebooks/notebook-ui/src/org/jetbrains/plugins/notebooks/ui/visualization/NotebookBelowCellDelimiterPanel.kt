package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.*

class NotebookBelowCellDelimiterPanel(
  val editor: EditorImpl,
  @Nls private val tooltipText: String?,
  private val executionCount: Int?,
  private val statusIcon: Icon?,
  private val isExecutable: Boolean,
  private val cellTags: List<String>,
  val cellNum: Int
) : JPanel(BorderLayout()) {
  private val notebookAppearance = editor.notebookAppearance
  // same as in the [org.jetbrains.plugins.notebooks.ui.visualization.NotebookAboveCellDelimiterPanelNew]
  private val standardDelimiterHeight = editor.notebookAppearance.cellBorderHeight / 4
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

    if (cellTags.isNotEmpty()) addTagsPanel() // PY-72712
  }

  private fun setPanelBackground() {
    background = when (isExecutable) {
      true -> notebookAppearance.getCodeCellBackground(editor.colorsScheme) ?: editor.colorsScheme.defaultBackground
      false -> editor.colorsScheme.defaultBackground
    }
  }

  private fun addTagsPanel() {  // WIP
    if (!Registry.`is`("jupyter.cell.metadata.tags", false)) return
    val tagsPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
      isOpaque = false
      border = JBUI.Borders.empty()
    }
    cellTags.forEach { tag -> tagsPanel.add(NotebookCellTagLabel(tag, cellNum)) }
    add(tagsPanel, BorderLayout.EAST)
  }

  private fun setBorder() {
    val dimension = when (editor.editorKind.isDiff()) {
      true -> Dimension(preferredSize.width, getJupyterCellSpacing(editor) / 2)
      false -> Dimension(preferredSize.width, standardDelimiterHeight)
    }
    border = BorderFactory.createEmptyBorder(dimension.height, 0, dimension.height, 0)
  }

  private fun getCollapsed(): Boolean {
    if (cellTags.isNotEmpty()) return false
    return !isExecutionCountDefined() && (tooltipText == null || statusIcon == ExpUiIcons.General.GreenCheckmark)
  }

  private fun isExecutionCountDefined(): Boolean = executionCount?.let { it > 0 } ?: false

  @Suppress("USELESS_ELVIS")
  override fun updateUI() {
    // This method is called within constructor of JPanel, at this time state is not yet initialized, reference is null.
    editor ?: return
    setPanelBackground()
    super.updateUI()
  }

}
