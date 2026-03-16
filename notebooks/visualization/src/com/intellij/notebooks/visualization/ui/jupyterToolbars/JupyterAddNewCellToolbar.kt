package com.intellij.notebooks.visualization.ui.jupyterToolbars

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/** Toolbar appearing in the editor center, between the cells on mouse hover, and always, after the last cell. */
@ApiStatus.Internal
class JupyterAddNewCellToolbar(
  // PY-66455
  actionGroup: ActionGroup,
  toolbarTargetComponent: JComponent,
  place: String = ActionPlaces.EDITOR_INLAY,
  actionsUpdatedCallback: (() -> Unit)? = null,
) : JupyterAbstractAboveCellToolbar(actionGroup, toolbarTargetComponent, place, actionsUpdatedCallback)