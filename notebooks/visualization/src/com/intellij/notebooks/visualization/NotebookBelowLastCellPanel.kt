// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.ui.jupyterToolbar.JupyterAddNewCellToolbar
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.Language
import java.awt.FlowLayout
import javax.swing.JPanel

/**
 * Basically, this panel consists only on a single "add new cell" toolbar.
 */
class NotebookBelowLastCellPanel(val editor: EditorImpl) : JPanel(FlowLayout(FlowLayout.CENTER)) {

  init {
    if (editor.isOrdinaryNotebookEditor()) {
      isOpaque = false
      border = JBUI.Borders.empty(editor.notebookAppearance.cellBorderHeight)
      val actionGroup = ActionManager.getInstance().getAction(ACTION_GROUP_ID) as ActionGroup
      add(JupyterAddNewCellToolbar(actionGroup, toolbarTargetComponent = this))
    }
  }

  companion object {
    @Language("devkit-action-id") private const val ACTION_GROUP_ID = "Jupyter.BelowCellNewPanel"
  }
}