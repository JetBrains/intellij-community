package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.UIFormXmlConstants
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JPanel

class NotebookAboveCellDelimiterPanel(val editor: Editor) : JPanel(BorderLayout()) {
  private var backgroundColor: Color = editor.colorsScheme.defaultBackground
  private var cellRoofColor: Color? = null
  private val standardDelimiterHeight = editor.notebookAppearance.CELL_BORDER_HEIGHT / 2

  // left them non-private to preserve compatibility with com.intellij.cwm.plugin.notebooks.AboveCellDelimiterConverter
  // todo: investigate what this AboveCellDelimiterConverter is used for and what to do about it
  var isCodeCell: Boolean = false
  val project get() = editor.project ?: ProjectManager.getInstance().defaultProject
  val actions = ArrayList<AnAction>()

  fun initialize(isCodeCell: Boolean, isFirstCell: Boolean) {
    if (editor.editorKind == EditorKind.DIFF) return
    this.isCodeCell = isCodeCell
    refreshColorScheme()

    val (delimiterPanel, roofPanel) = createRoofAndDelimiterPanels(cellRoofColor, isFirstCell)

    add(delimiterPanel, BorderLayout.NORTH)
    add(roofPanel, BorderLayout.SOUTH)

    listenForColorSchemeChanges()
  }

  private fun createRoofAndDelimiterPanels(cellRoofColor: Color?, isFirstCell: Boolean): Pair<JPanel, JPanel> {
    val delimiterPanel = JPanel()
    val delimiterPanelHeight = when (isFirstCell) {
      true -> FIRST_CELL_DELIMITER_HEIGHT
      false -> standardDelimiterHeight
    }
    delimiterPanel.background = backgroundColor
    delimiterPanel.preferredSize = Dimension(JBUIScale.scale(1), delimiterPanelHeight)

    val roofPanel = JPanel()
    roofPanel.background = cellRoofColor
    roofPanel.preferredSize = Dimension(JBUIScale.scale(1), standardDelimiterHeight)

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

  companion object {
    private val FIRST_CELL_DELIMITER_HEIGHT = JBUIScale.scale(24)  // see figma PY-66455
  }
}
