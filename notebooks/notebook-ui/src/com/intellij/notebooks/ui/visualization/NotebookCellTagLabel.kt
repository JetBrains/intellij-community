package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JLabel

class NotebookCellTagLabel(@NlsSafe val tag: String, val cellIndex: Int) : JLabel(tag) {  // PY-72712
  init {
    font = JBFont.small()
    foreground = JBColor.BLACK
    isOpaque = false
    border = BorderFactory.createEmptyBorder(TEXT_VERTICAL_PADDING, TEXT_HORIZONTAL_PADDING, TEXT_VERTICAL_PADDING, TEXT_HORIZONTAL_PADDING)

    PopupHandler.installPopupMenu(this, "JupyterCellTagActionGroup", "NotebookCellTagPopup")
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.color = backgroundColor
      g2.fillRoundRect(0, 0, width, height, ARC_SIZE, ARC_SIZE)
    }
    finally {
      g2.dispose()
    }
    super.paintComponent(g)
  }

  companion object {
    private val TEXT_VERTICAL_PADDING = JBUI.scale(2)
    private val TEXT_HORIZONTAL_PADDING = JBUI.scale(8)
    private val ARC_SIZE = JBUI.scale(20)
    private var backgroundColor = JBColor.namedColor("Tag.background", Gray.xDF)
  }
}