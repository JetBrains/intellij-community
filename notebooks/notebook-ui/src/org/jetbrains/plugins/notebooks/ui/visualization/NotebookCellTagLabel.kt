package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.actionSystem.*
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBFont
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JLabel

class NotebookCellTagLabel(@Nls val tag: String, val cellNum: Int) : JLabel(tag) {  // PY-72712 WIP

  private var backgroundColor = JBColor.LIGHT_GRAY
  private var foregroundColor = JBColor.BLACK

  init {
    font = JBFont.small()
    foreground = foregroundColor
    isOpaque = false
    border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        showPopup( { createPopupGroup() }, comp, x, y)
      }
    })
  }

  private fun showPopup(popupBuilder: () -> ActionGroup, component: Component, x: Int, y: Int) {
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, popupBuilder.invoke())
    popupMenu.component.show(component, x, y)
  }

  private fun createPopupGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()
    val action = ActionManager.getInstance().getAction("JupyterCellRemoveTagAction")
    group.add(action)
    return group
  }

  override fun paintComponent(g: Graphics) {
    (g as? Graphics2D)?.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = backgroundColor
    g.fillRoundRect(0, 0, width, height, 20 , 20)

    val fm = g.fontMetrics
    val textHeight = fm.height
    val textWidth =  fm.stringWidth(text)
    val textX = (width - textWidth) / 2
    val textY = (height - textHeight) / 2 + fm.ascent
    g.color = foregroundColor
    g.drawString(text, textX, textY)
  }
}