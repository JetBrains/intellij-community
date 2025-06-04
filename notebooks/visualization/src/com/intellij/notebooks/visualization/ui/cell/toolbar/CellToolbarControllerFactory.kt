// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.toolbar

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedControllerFactory
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.util.registry.Registry

class CellToolbarControllerFactory : SelfManagedControllerFactory {
  override fun createController(editorCell: EditorCell): SelfManagedCellController? {
    if (!Registry.`is`("jupyter.per.cell.management.actions.toolbar") ||
        !editorCell.editor.isOrdinaryNotebookEditor()) {
      return null
    }

    return EditorCellActionsToolbarController(editorCell)
  }
}