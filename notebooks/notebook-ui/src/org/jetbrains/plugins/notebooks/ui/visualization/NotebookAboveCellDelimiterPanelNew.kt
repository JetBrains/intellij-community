package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.UIFormXmlConstants
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JPanel

class NotebookAboveCellDelimiterPanelNew(val editor: Editor) : JPanel(BorderLayout()) {
  private var backgroundColor: Color = editor.colorsScheme.defaultBackground
  private var cellRoofColor: Color? = null
  private var isCodeCell: Boolean = false

  fun initialize(isCodeCell: Boolean) {
    if (editor.editorKind == EditorKind.DIFF) return
    this.isCodeCell = isCodeCell
    refreshColorScheme()

    val (delimiterPanel, roofPanel) = createRoofAndDelimiterPanels(cellRoofColor)

    add(delimiterPanel, BorderLayout.NORTH)
    add(roofPanel, BorderLayout.SOUTH)

    listenForColorSchemeChanges()
  }

  private fun createRoofAndDelimiterPanels(cellRoofColor: Color?): Pair<JPanel, JPanel> {
    val delimiterPanel = JPanel()
    delimiterPanel.background = backgroundColor
    delimiterPanel.preferredSize = Dimension(JBUIScale.scale(1), editor.notebookAppearance.CELL_BORDER_HEIGHT / 2)

    val roofPanel = JPanel()
    roofPanel.background = cellRoofColor
    roofPanel.preferredSize = Dimension(JBUIScale.scale(1), editor.notebookAppearance.CELL_BORDER_HEIGHT / 2)

    return Pair(delimiterPanel, roofPanel)
  }

  private fun listenForColorSchemeChanges() = addPropertyChangeListener(UIFormXmlConstants.ELEMENT_BORDER) { updateComponentColors() }

  private fun refreshColorScheme() {
    backgroundColor = editor.colorsScheme.defaultBackground
    cellRoofColor = if (isCodeCell) editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme) else backgroundColor
  }

  private fun updateComponentColors() {
    refreshColorScheme()
    if (components.isNotEmpty()) {
      val delimiterPanel = components[0] as? JPanel
      val roofPanel = components[1] as? JPanel

      delimiterPanel?.background = backgroundColor
      roofPanel?.background = cellRoofColor
    }
  }
}
