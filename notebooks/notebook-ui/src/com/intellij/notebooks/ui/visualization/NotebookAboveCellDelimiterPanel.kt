package com.intellij.notebooks.ui.visualization

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.uiDesigner.UIFormXmlConstants
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isDiff
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

class NotebookAboveCellDelimiterPanel(
  val editor: Editor,
  val isCodeCell: Boolean,
  isFirstCell: Boolean
) : JPanel(BorderLayout()) {
  private var delimiterPanel: JPanel? = null
  private var roofPanel: JPanel? = null

  private var backgroundColor: Color = editor.colorsScheme.defaultBackground
  private var cellRoofColor: Color? = null
  private var isHighlighted: Boolean = false
  private val standardDelimiterHeight = editor.notebookAppearance.cellBorderHeight / 2

  private val isConsole = editor.getUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW) == true
  private val delimiterPanelHeight = when {
    isFirstCell && isConsole -> 0  // todo: maybe for this case just don't show delimiterPanel?
    isFirstCell -> editor.notebookAppearance.aboveFirstCellDelimiterHeight
    else ->  editor.notebookAppearance.distanceBetweenCells
  }

  val project get() = editor.project ?: ProjectManager.getInstance().defaultProject

  init {
    if (!editor.editorKind.isDiff()) {
      refreshColorScheme()
      createRoofAndDelimiterPanels(cellRoofColor)
      delimiterPanel?.let { add(it, BorderLayout.NORTH) }
      roofPanel?.let { add(it, BorderLayout.SOUTH) }
      listenForColorSchemeChanges()
    }
  }

  fun addHighlight() {
    isHighlighted = true
    delimiterPanel?.repaint()
  }

  fun removeHighlight() {
    isHighlighted = false
    delimiterPanel?.repaint()
  }

  private fun createRoofAndDelimiterPanels(cellRoofColor: Color?) {
    delimiterPanel = object : JPanel() {
      override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (isHighlighted) {
          val g2d = g as Graphics2D
          g2d.color = JBColor.BLUE
          val y = height / 2
          g2d.fillRect(0, y - 1, width, 2)
        }
      }
    }.also {
      it.background = backgroundColor
      it.preferredSize = Dimension(JBUIScale.scale(1), delimiterPanelHeight)
    }

    roofPanel = JPanel().also {
      it.background = cellRoofColor
      it.preferredSize = Dimension(JBUIScale.scale(1), standardDelimiterHeight)
    }
  }

  private fun listenForColorSchemeChanges() = addPropertyChangeListener(UIFormXmlConstants.ELEMENT_BORDER) { updateComponentColors() }

  private fun refreshColorScheme() {
    backgroundColor = editor.colorsScheme.defaultBackground
    cellRoofColor = if (isCodeCell) editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme) else backgroundColor
  }

  private fun updateComponentColors() {
    refreshColorScheme()
    if (components.isNotEmpty()) {
      delimiterPanel?.background = backgroundColor
      roofPanel?.background = cellRoofColor
    }
  }
}
