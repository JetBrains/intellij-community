package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.NewUiValue
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import java.awt.AlphaComposite
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
  var alpha = 1.0f

  init {
    val borderColor = if (NewUiValue.isEnabled()) {
      JBColor.LIGHT_GRAY
    } else {
      JBColor.DARK_GRAY
    }

    border = BorderFactory.createCompoundBorder(RoundedLineBorder(borderColor, TOOLBAR_ARC_SIZE, TOOLBAR_BORDER_THICKNESS),
                                                BorderFactory.createEmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING))
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    // todo: it would be preferable to store the cell number right here instead of the first line number
    putClientProperty(JUPYTER_TOOLBAR_LINE_POSITION_KEY, firstLine)
    setSkipWindowAdjustments(false)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
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
    private val OUTER_PADDING = JBUI.scale(3)
    val JUPYTER_TOOLBAR_LINE_POSITION_KEY = Key.create<Int>("JUPYTER_TOOLBAR_LINE_POSITION_KEY")
  }
}