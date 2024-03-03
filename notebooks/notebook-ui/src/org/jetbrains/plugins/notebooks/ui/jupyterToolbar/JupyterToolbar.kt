package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @See com.intellij.bigdatatools.visualization.inlays.components.FadingToolbar
 * PY-66455
 */
class JupyterToolbarImpl(
  actionGroup: ActionGroup
) : ActionToolbarImpl(ActionPlaces.EDITOR_INLAY, actionGroup, true) {
  init {
    setSkipWindowAdjustments(false)
    isReservePlaceAutoPopupIcon = true
    isOpaque = false
    println("JupyterToolbarImpl: $preferredSize")
  }
}

class JupyterToolbar(actionGroup: ActionGroup, targetComponent: JComponent) : JPanel() {
  private var actionToolbar: JupyterToolbarImpl = JupyterToolbarImpl(actionGroup)

  init {
    layout = BorderLayout()

    actionToolbar.targetComponent = targetComponent
    add(actionToolbar.component, BorderLayout.CENTER)

    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    enableEvents(MouseEvent.MOUSE_EVENT_MASK or MouseEvent.MOUSE_MOTION_EVENT_MASK)

    border = RoundedLineBorder(JBColor.LIGHT_GRAY, JBUI.scale(14), JBUI.scale(1))
    background = JBColor.WHITE
    isOpaque = false
    println("JupyterToolbar: $preferredSize")
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.color = this.background
    g2.fillRoundRect(0, 0, width, height, JBUI.scale(14), JBUI.scale(14))
    g2.dispose()
  }
}
