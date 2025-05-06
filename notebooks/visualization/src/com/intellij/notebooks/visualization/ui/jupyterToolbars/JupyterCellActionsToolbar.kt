package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/** Floating toolbar appearing for selected and hovered cell in the top right corner of cell.*/
@ApiStatus.Internal
class JupyterCellActionsToolbar(
  // PY-72283
  actionGroup: ActionGroup,
  target: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY,
  actionsUpdatedCallback: () -> Unit,
) : JupyterAbstractAboveCellToolbar(actionGroup, target, place, actionsUpdatedCallback) {

  init {
    background = EditorColorsManager.getInstance().globalScheme.defaultBackground
  }

  override fun fillRect(g2: Graphics2D) {
    val arcSize = getArcSize()
    val shape = RoundRectangle2D.Float(
      TOOLBAR_BORDER_THICKNESS / 2f, TOOLBAR_BORDER_THICKNESS / 2f,
      width - TOOLBAR_BORDER_THICKNESS.toFloat(), height - TOOLBAR_BORDER_THICKNESS.toFloat(),
      arcSize.toFloat(), arcSize.toFloat()
    )

    g2.clip(shape)
    g2.fill(shape)
  }

  override fun getArcSize(): Int = JBUI.scale(8)
  override fun getHorizontalPadding(): Int = JBUI.scale(4)
  override fun getVerticalPadding(): Int = JBUI.scale(1)
}