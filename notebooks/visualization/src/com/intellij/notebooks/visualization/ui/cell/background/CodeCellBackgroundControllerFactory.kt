// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.background

import com.intellij.notebooks.ui.visualization.NotebookUtil.isDiffKind
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedControllerFactory
import com.intellij.notebooks.visualization.ui.EditorCell

class CodeCellBackgroundControllerFactory : SelfManagedControllerFactory {
  override fun createController(editorCell: EditorCell): SelfManagedCellController? {
    if (editorCell.editor.isDiffKind())
      return null
    if (editorCell.interval.type == NotebookCellLines.CellType.MARKDOWN)
      return null
    return NotebookCellBackgroundController(editorCell)
  }
}