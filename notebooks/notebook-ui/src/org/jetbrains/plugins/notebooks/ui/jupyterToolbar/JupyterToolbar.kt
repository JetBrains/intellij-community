package org.jetbrains.plugins.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.editor.Editor
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
import javax.swing.JComponent
import javax.swing.SwingUtilities


/**
 * @See com.intellij.bigdatatools.visualization.inlays.components.FadingToolbar
 * PY-66455
 */
class JupyterToolbar(actionGroup: ActionGroup, target: JComponent) :
  ActionToolbarImpl(ActionPlaces.EDITOR_INLAY, actionGroup, true)
{
  init {
    val borderColor = when (NewUiValue.isEnabled()) {
      true -> JBColor.LIGHT_GRAY
      else -> JBColor.GRAY
    }
    border = BorderFactory.createCompoundBorder(RoundedLineBorder(borderColor, TOOLBAR_ARC_SIZE, TOOLBAR_BORDER_THICKNESS),
                                                BorderFactory.createEmptyBorder(OUTER_PADDING, OUTER_PADDING, OUTER_PADDING, OUTER_PADDING))
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    targetComponent = target
    setSkipWindowAdjustments(false)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA)
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
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
  }

  fun getRespectiveLineNumberInEditor(editor: Editor): Int? {
    val point = SwingUtilities.convertPoint(this, 0, this.height, editor.contentComponent)
    return point.y
      .takeIf { it >= 0 }
      ?.let { editor.xyToLogicalPosition(point).line }
  }

  companion object {
    private const val ALPHA = 1.0f
    private val TOOLBAR_ARC_SIZE = JBUI.scale(14)
    private val TOOLBAR_BORDER_THICKNESS = JBUI.scale(1)
    private val OUTER_PADDING = JBUI.scale(3)
  }
}