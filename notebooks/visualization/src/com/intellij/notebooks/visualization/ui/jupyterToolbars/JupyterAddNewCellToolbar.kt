package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/** Toolbar appearing in the editor center, between the cells on mouse hover, and always, after the last cell. */
@ApiStatus.Internal
class JupyterAddNewCellToolbar(
  // PY-66455
  actionGroup: ActionGroup,
  toolbarTargetComponent: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY,
) : JupyterAbstractAboveCellToolbar(actionGroup, toolbarTargetComponent, place, actionsUpdatedCallback = null) {

  override fun getArcSize(): Int = JBUI.scale(16)
  override fun getHorizontalPadding(): Int = JBUI.scale(3)
}