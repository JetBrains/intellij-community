package com.intellij.notebooks.ui.jupyterToolbar

import com.intellij.notebooks.ui.SelectClickedCellEventHelper
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.ui.JBColor
import com.intellij.ui.NewUiValue
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.BorderFactory
import javax.swing.JComponent

@ApiStatus.Internal
abstract class JupyterAbstractAboveCellToolbar(
  actionGroup: ActionGroup,
  target: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY
): ActionToolbarImpl(place, actionGroup, true) {
  init {
    val borderColor = when (NewUiValue.isEnabled()) {
      true -> JBColor.LIGHT_GRAY
      else -> JBColor.GRAY
    }
    border = BorderFactory.createCompoundBorder(RoundedLineBorder(borderColor, getArcSize(), TOOLBAR_BORDER_THICKNESS),
                                                BorderFactory.createEmptyBorder(getVerticalPadding(), getHorizontalPadding(), getVerticalPadding(), getHorizontalPadding()))
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    targetComponent = target
    putClientProperty(SelectClickedCellEventHelper.SKIP_CLICK_PROCESSING_FOR_CELL_SELECTION, true)
    setSkipWindowAdjustments(false)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA)
      g2.color = this.background
      g2.fillRoundRect(0, 0, width, height, getArcSize(), getArcSize())
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

  override fun installPopupHandler(customizable: Boolean, popupActionGroup: ActionGroup?, popupActionId: String?) = Unit
  protected abstract fun getArcSize(): Int
  protected abstract fun getHorizontalPadding(): Int
  protected open fun getVerticalPadding(): Int = getHorizontalPadding()

  companion object {
    private const val ALPHA = 1.0f
    private val TOOLBAR_BORDER_THICKNESS = JBUI.scale(1)
  }
}