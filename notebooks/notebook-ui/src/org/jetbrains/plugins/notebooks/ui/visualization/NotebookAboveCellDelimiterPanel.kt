package org.jetbrains.plugins.notebooks.ui.visualization

import JupyterCellBorderButton
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.ProjectManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel

class NotebookAboveCellDelimiterPanel(val editor: Editor) : JPanel(GridBagLayout()) {
  fun initialize(actions: Array<AnAction>) {
    val cellRoofColor = editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
    val backgroundColor = editor.colorsScheme.defaultBackground
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
      val width =  button.preferredSize.width
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

class HidingMouseListener(
  private val mainComponent: JComponent,
  private vararg val componentsToHide: Component
) : MouseAdapter() {
  override fun mouseEntered(e: MouseEvent) {
    for (c in componentsToHide) {
      c.isVisible = true
    }
  }

  override fun mouseExited(e: MouseEvent) {
    if (e.point !in mainComponent.bounds) {
      for (c in componentsToHide) {
        c.isVisible = false
      }
    }
  }
}