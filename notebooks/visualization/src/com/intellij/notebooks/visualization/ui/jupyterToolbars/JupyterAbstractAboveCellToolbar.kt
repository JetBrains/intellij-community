// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.notebooks.ui.SelectClickedCellEventHelper
import com.intellij.notebooks.visualization.useG2D
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import com.intellij.ui.NewUiValue
import com.intellij.ui.RoundedLineBorder
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.BorderFactory
import javax.swing.JComponent

/** Base class for the floating-toolbar in the top right corner of the cell,
 * and for new-cells-creation-toolbar appearing between cells. */
@ApiStatus.Internal
abstract class JupyterAbstractAboveCellToolbar(
  actionGroup: ActionGroup,
  toolbarTargetComponent: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY,
  private val actionsUpdatedCallback: (() -> Unit)? = null,
) : ActionToolbarImpl(place, actionGroup, true) {

  init {
    isOpaque = false
    targetComponent = toolbarTargetComponent
    cursor = Cursor.getDefaultCursor()
    val borderColor = when (NewUiValue.isEnabled()) {
      true -> JBColor.namedColor("Editor.Toolbar.borderColor", JBColor.border())
      else -> JBColor.GRAY
    }
    border = BorderFactory.createCompoundBorder(RoundedLineBorder(borderColor, getArcSize(), TOOLBAR_BORDER_THICKNESS),
                                                BorderFactory.createEmptyBorder(getVerticalPadding(),
                                                                                getHorizontalPadding(),
                                                                                getVerticalPadding(),
                                                                                getHorizontalPadding()))
    putClientProperty(SelectClickedCellEventHelper.SKIP_CLICK_PROCESSING_FOR_CELL_SELECTION, true)
  }

  override fun actionsUpdated(forceRebuild: Boolean, newVisibleActions: List<AnAction>) {
    super.actionsUpdated(forceRebuild, newVisibleActions)
    actionsUpdatedCallback?.invoke()
  }

  override fun paintComponent(g: Graphics) {
    g.useG2D { g2d ->
      g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA)
      g2d.color = this.background
      fillRect(g2d)
    }
  }

  protected open fun fillRect(g2d: Graphics2D) {
    g2d.fillRoundRect(0, 0, width, height, getArcSize(), getArcSize())
  }

  protected fun getArcSize(): Int = JBUI.scale(8)
  protected fun getHorizontalPadding(): Int = JBUI.scale(3)
  protected fun getVerticalPadding(): Int = JBUI.scale(3)

  companion object {
    const val ALPHA: Float = 1.0f
    val TOOLBAR_BORDER_THICKNESS: Int = JBUI.scale(1)
  }
}