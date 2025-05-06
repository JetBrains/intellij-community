package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
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

  override fun getArcSize(): Int = JBUI.scale(8)
  override fun getHorizontalPadding(): Int = JBUI.scale(4)
  override fun getVerticalPadding(): Int = JBUI.scale(1)
}