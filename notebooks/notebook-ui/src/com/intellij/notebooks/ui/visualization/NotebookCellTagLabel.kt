package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JLabel

class NotebookCellTagLabel(@NlsSafe val tag: String, val cellNum: Int) : JLabel(tag) {  // PY-72712
  private var backgroundColor = JBColor.namedColor("Tag.background", Gray.xDF)
  private var foregroundColor = JBColor.BLACK

  init {
    font = JBFont.small()
    foreground = foregroundColor
    isOpaque = false
    border = BorderFactory.createEmptyBorder(TEXT_VERTICAL_PADDING, TEXT_HORIZONTAL_PADDING, TEXT_VERTICAL_PADDING, TEXT_HORIZONTAL_PADDING)

    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) = showPopup( { createPopupGroup() }, comp, x, y)
    })
  }

  private fun showPopup(popupBuilder: () -> ActionGroup, component: Component, x: Int, y: Int) {
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, popupBuilder.invoke())
    popupMenu.component.show(component, x, y)
  }

  private fun createPopupGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()
    val action = ActionManager.getInstance().getAction(ACTION_ID)
    group.add(action)
    return group
  }

  override fun paintComponent(g: Graphics) {
    (g as? Graphics2D)?.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = backgroundColor
    g.fillRoundRect(0, 0, width, height, ARC_SIZE , ARC_SIZE)

    val fm = g.fontMetrics
    val textHeight = fm.height
    val textWidth =  fm.stringWidth(text)
    val textX = (width - textWidth) / 2
    val textY = (height - textHeight) / 2 + fm.ascent
    g.color = foregroundColor
    g.drawString(text, textX, textY)
  }

  companion object {
    private val TEXT_VERTICAL_PADDING = JBUI.scale(2)
    private val TEXT_HORIZONTAL_PADDING = JBUI.scale(8)
    private val ARC_SIZE = JBUI.scale(20)
    private const val ACTION_ID = "JupyterCellRemoveTagAction"
  }
}