package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.uiDesigner.UIFormXmlConstants
import java.awt.*
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel

internal typealias BackgroundColorToCellRoofColor = Pair<Color, Color?>

class NotebookAboveCellDelimiterPanel(val editor: Editor) : JPanel(GridBagLayout()) {

  internal inner class ColorsChangeListenerAdapter {
    private lateinit var currentColorsPalette: BackgroundColorToCellRoofColor

    private fun recreatePalette() {
      val newBackgroundColor = editor.colorsScheme.defaultBackground
      val newCellRoofTopColor = if (isCodeCell)
        editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
      else newBackgroundColor

      currentColorsPalette = newBackgroundColor to newCellRoofTopColor
    }

    private fun updateColorsForCellBorderPanels(cellBordersPanels: Iterable<JPanel>) {
      for (panel in cellBordersPanels) {
        val (left, right) = panel.components
        left.background = currentColorsPalette.first
        right.background = currentColorsPalette.second
      }
    }

    fun propertyChanged(
      backgroundColor: Color,
      delimiterPanel: JPanel,
      codeRoofPanel: JPanel,
      cellBordersPanels: Iterable<JPanel>,
    ) {
      if (::currentColorsPalette.isInitialized && backgroundColor == currentColorsPalette.first) return

      recreatePalette()
      delimiterPanel.background = currentColorsPalette.first
      codeRoofPanel.background = currentColorsPalette.second

      updateColorsForCellBorderPanels(cellBordersPanels)
    }
  }

  private val colorsChangeListenerAdapter = ColorsChangeListenerAdapter()
  val actions = ArrayList<AnAction>()
  var isCodeCell = false

  fun initialize(actions: Array<AnAction>, isCodeCell: Boolean) {
    if (editor.editorKind == EditorKind.DIFF) return

    this.actions.addAll(actions)
    this.isCodeCell = isCodeCell

    val backgroundColor = editor.colorsScheme.defaultBackground
    val cellRoofColor = if (isCodeCell) editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme) else backgroundColor

    val leftPanel = createCellDelimiterPanel(backgroundColor, cellRoofColor)
    val rightPanel = createCellDelimiterPanel(backgroundColor, cellRoofColor)

    val centerPanel = JLayeredPane()
    val delimiterPanel = JPanel()
    delimiterPanel.background = backgroundColor
    centerPanel.add(delimiterPanel)

    val codeRoofPanel = JPanel()
    codeRoofPanel.background = cellRoofColor
    centerPanel.add(codeRoofPanel)

    var xOffset = 0
    val buttons = mutableListOf<JComponent>()
    for (action in actions) {
      val button = JupyterCellBorderButton(editor as EditorEx, action)
      button.initialize()
      val width = button.preferredSize.width
      button.setBounds(xOffset, 0, width, editor.notebookAppearance.CELL_BORDER_HEIGHT)
      xOffset += width
      centerPanel.add(button, JLayeredPane.DEFAULT_LAYER)
      buttons.add(button)
    }

    centerPanel.preferredSize = Dimension(xOffset, editor.notebookAppearance.CELL_BORDER_HEIGHT)
    delimiterPanel.setBounds(0, 0, xOffset, editor.notebookAppearance.CELL_BORDER_HEIGHT / 2)
    codeRoofPanel.setBounds(0, editor.notebookAppearance.CELL_BORDER_HEIGHT / 2, xOffset, editor.notebookAppearance.CELL_BORDER_HEIGHT / 2)

    val c = GridBagConstraints()
    c.weightx = 1.0
    c.fill = GridBagConstraints.HORIZONTAL
    add(leftPanel, c)

    c.weightx = 0.0
    c.gridx = 1
    add(centerPanel, c)

    c.gridx = 2
    c.weightx = 1.0
    add(rightPanel, c)

    mouseListeners.firstOrNull { it is HidingMouseListener }?.let(this::removeMouseListener)
    HidingMouseListener(this, *buttons.toTypedArray()).let {
      // It doesn't look like a correct way of handling mouse events.
      // When the cursor enters the button, it also triggers exit event in the parent container.
      // There should be a better way to not trigger exit event inside the whole container.
      addMouseListener(it)
      for (button in buttons) {
        button.addMouseListener(it)
      }
    }

    addPropertyChangeListener(UIFormXmlConstants.ELEMENT_BORDER) {
      val newBackgroundColor = editor.colorsScheme.defaultBackground
      colorsChangeListenerAdapter.propertyChanged(
        newBackgroundColor,
        delimiterPanel, codeRoofPanel, listOf(leftPanel, rightPanel)
      )
    }
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

  override fun getHeight(): Int {
    return editor.notebookAppearance.CELL_BORDER_HEIGHT
  }

  val project get() = editor.project ?: ProjectManager.getInstance().defaultProject
}
