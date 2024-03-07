package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.BorderFactory

/**
 * @See com.intellij.bigdatatools.visualization.inlays.components.FadingToolbar
 * PY-66455
 */
class JupyterToolbar(actionGroup: ActionGroup) : ActionToolbarImpl(ActionPlaces.EDITOR_INLAY, actionGroup, true) {
  init {
    setSkipWindowAdjustments(false)
    border = BorderFactory.createCompoundBorder(RoundedLineBorder(JBColor.LIGHT_GRAY, JBUI.scale(14), JBUI.scale(1)),
                                                BorderFactory.createEmptyBorder(2, 3, 2, 3))
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    isOpaque = false
    println("JupyterToolbar: $preferredSize")
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.color = this.background
      g2.fillRoundRect(0, 0, width, height, JBUI.scale(14), JBUI.scale(14))
    }
    finally {
      g2.dispose()
    }
  }

  override fun updateUI() {
    super.updateUI()
    if (!StartupUiUtil.isDarkTheme) {
      background = JBColor.WHITE
    }
  }
}