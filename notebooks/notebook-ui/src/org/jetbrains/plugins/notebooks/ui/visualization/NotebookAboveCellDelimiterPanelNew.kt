package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import java.awt.*
import javax.swing.JPanel

class NotebookAboveCellDelimiterPanelNew(val editor: Editor) : JPanel(GridBagLayout()) {
  // todo: handle switch between dark/light themes
  private var isCodeCell: Boolean = false

  fun initialize(isCodeCell: Boolean) {
    if (editor.editorKind == EditorKind.DIFF) return

    this.isCodeCell = isCodeCell

    val backgroundColor = editor.colorsScheme.defaultBackground
    val cellRoofColor = if (isCodeCell) editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme) else backgroundColor
    val basePanel = createCellDelimiterPanel(backgroundColor, cellRoofColor)

    val c = GridBagConstraints()
    c.weightx = 1.0
    c.fill = GridBagConstraints.HORIZONTAL
    add(basePanel, c)
  }

  private fun createCellDelimiterPanel(backgroundColor: Color, cellRoofColor: Color?): JPanel {
    val result = JPanel(BorderLayout())

    val delimiterPanel = JPanel()
    delimiterPanel.background = backgroundColor
    delimiterPanel.preferredSize = Dimension(1, 10)

    val roofPanel = JPanel()
    roofPanel.background = cellRoofColor
    roofPanel.preferredSize = Dimension(1, 10)

    result.add(delimiterPanel, BorderLayout.NORTH)
    result.add(roofPanel, BorderLayout.SOUTH)
    return result
  }
}
