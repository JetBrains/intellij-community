package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.util.Key
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
class JupyterToolbar(actionGroup: ActionGroup,
                     firstLine: Int = 0
) : ActionToolbarImpl(ActionPlaces.EDITOR_INLAY, actionGroup, true) {
  init {
    border = BorderFactory.createCompoundBorder(RoundedLineBorder(JBColor.LIGHT_GRAY, TOOLBAR_ARC_SIZE, TOOLBAR_BORDER_THICKNESS),
                                                BorderFactory.createEmptyBorder(2, 3, 2, 3))
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    isOpaque = false
    // todo: it would be preferable to set NotebookCellLines.Interval right here
    putClientProperty(JUPYTER_TOOLBAR_LINE_POSITION_KEY, firstLine)
    setSkipWindowAdjustments(false)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.color = this.background
      g2.fillRoundRect(0, 0, width, height, TOOLBAR_ARC_SIZE, TOOLBAR_ARC_SIZE)
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

  companion object {
    private val TOOLBAR_ARC_SIZE = JBUI.scale(14)
    private val TOOLBAR_BORDER_THICKNESS = JBUI.scale(1)
    val JUPYTER_TOOLBAR_LINE_POSITION_KEY = Key.create<Int>("JUPYTER_TOOLBAR_LINE_POSITION_KEY")
  }
}