package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/** Floating toolbar appearing for selected and hovered cell in the top right corner of the cell.*/
@ApiStatus.Internal
class JupyterCellActionsToolbar(
  // PY-72283
  actionGroup: ActionGroup,
  target: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY,
  actionsUpdatedCallback: () -> Unit,
) : JupyterAbstractAboveCellToolbar(actionGroup, target, place, actionsUpdatedCallback) {
  override fun fillRect(g2d: Graphics2D) {
    val arcSize = getArcSize()
    val shape = RoundRectangle2D.Float(
      TOOLBAR_BORDER_THICKNESS / 2f, TOOLBAR_BORDER_THICKNESS / 2f,
      width - TOOLBAR_BORDER_THICKNESS.toFloat(), height - TOOLBAR_BORDER_THICKNESS.toFloat(),
      arcSize.toFloat(), arcSize.toFloat()
    )

    g2d.clip(shape)
    g2d.fill(shape)
  }
}