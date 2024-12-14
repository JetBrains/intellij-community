// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.getCells
import com.intellij.notebooks.visualization.getSelectionLines
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.FloatingToolbar
import com.intellij.openapi.editor.Editor
import kotlinx.coroutines.CoroutineScope

class MarkdownCellsFloatingToolbar(
  editor: Editor,
  coroutineScope: CoroutineScope,
): FloatingToolbar(editor, coroutineScope) {
  override fun isEnabled(): Boolean {
    val selectedCells = getSelectedCells()
    if (selectedCells.isEmpty() || selectedCells.size > 1) return false
    return selectedCells.first().type == NotebookCellLines.CellType.MARKDOWN
  }

  private fun getSelectedCells(): List<NotebookCellLines.Interval> {
    val notebookCellLines = NotebookCellLines.get(editor)
    return editor.caretModel.allCarets.flatMap { caret ->
      notebookCellLines.getCells(editor.document.getSelectionLines(caret))
    }.distinct()
  }

  override fun createActionGroup(): ActionGroup? {
    return CustomActionsSchema.getInstance().getCorrectedAction("Markdown.Toolbar.Floating") as? ActionGroup
  }
}
