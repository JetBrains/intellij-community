package com.intellij.notebooks.ui.editor.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.project.DumbAware

/**
 * Marker interface to distinguish Jupyter actions invoked in editor from all other actions.
 *
 * May be used e.g. by [ActionPromoter].
 */
interface JupyterEditorAction : DumbAware {
  val availableInReadOnly: Boolean get() = false
}