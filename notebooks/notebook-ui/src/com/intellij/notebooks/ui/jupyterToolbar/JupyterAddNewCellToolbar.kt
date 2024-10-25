package com.intellij.notebooks.ui.jupyterToolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class JupyterAddNewCellToolbar(  // PY-66455
  actionGroup: ActionGroup,
  target: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY
) : JupyterAbstractAboveCellToolbar(actionGroup, target, place) {

  override fun getArcSize(): Int = JBUI.scale(16)
  override fun getHorizontalPadding() = JBUI.scale(3)
}