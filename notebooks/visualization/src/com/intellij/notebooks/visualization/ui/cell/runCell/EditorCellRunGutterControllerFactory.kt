// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.runCell

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedControllerFactory
import com.intellij.notebooks.visualization.ui.EditorCell

class EditorCellRunGutterControllerFactory : SelfManagedControllerFactory {
  override fun createController(editorCell: EditorCell): SelfManagedCellController? {
    if (!shouldShowRunButton(editorCell)) {
      return null
    }

    return EditorCellRunGutterController(editorCell)
  }

  private fun shouldShowRunButton(cell: EditorCell): Boolean {
    return cell.editor.isOrdinaryNotebookEditor() &&
           cell.editor.notebookAppearance.shouldShowRunButtonInGutter() &&
           cell.type == NotebookCellLines.CellType.CODE
  }
}