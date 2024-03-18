package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.UIFormXmlConstants
import java.awt.*
import javax.swing.JPanel

class NotebookAboveCellDelimiterPanelNew(val editor: Editor) : JPanel(GridBagLayout()) {
  private var backgroundColor: Color = editor.colorsScheme.defaultBackground
  private var cellRoofColor: Color? = null
  private var isCodeCell: Boolean = false

  fun initialize(isCodeCell: Boolean) {
    if (editor.editorKind == EditorKind.DIFF) return
    this.isCodeCell = isCodeCell

    refreshColorScheme()
    val basePanel = createRoofAndDelimiterPanels(backgroundColor, cellRoofColor)

    val c = GridBagConstraints()
    c.weightx = 1.0
    c.fill = GridBagConstraints.HORIZONTAL
    add(basePanel, c)

    listenForColorSchemeChanges()
  }

  private fun createRoofAndDelimiterPanels(backgroundColor: Color, cellRoofColor: Color?): JPanel {
    val result = JPanel(BorderLayout())

    val delimiterPanel = JPanel()
    delimiterPanel.background = backgroundColor
    delimiterPanel.preferredSize = Dimension(JBUIScale.scale(1), editor.notebookAppearance.CELL_BORDER_HEIGHT / 2)

    val roofPanel = JPanel()
    roofPanel.background = cellRoofColor
    roofPanel.preferredSize = Dimension(JBUIScale.scale(1), editor.notebookAppearance.CELL_BORDER_HEIGHT / 2)

    result.add(delimiterPanel, BorderLayout.NORTH)
    result.add(roofPanel, BorderLayout.SOUTH)
    return result
  }

  private fun listenForColorSchemeChanges() = addPropertyChangeListener(UIFormXmlConstants.ELEMENT_BORDER) { updateComponentColors() }

  private fun refreshColorScheme() {
    backgroundColor = editor.colorsScheme.defaultBackground
    cellRoofColor = if (isCodeCell) editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme) else backgroundColor
  }

  private fun updateComponentColors() {
    refreshColorScheme()
    val basePanel = components[0]

    if (basePanel is JPanel && basePanel.layout is BorderLayout) {
      (basePanel.getComponent(0) as? JPanel)?.background = backgroundColor  // delimiterPanel
      (basePanel.getComponent(1) as? JPanel)?.background = cellRoofColor // roofPanel
    }
  }
}
