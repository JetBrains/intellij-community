// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grid.impl.ide

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.CsvDocumentDataHookUp
import com.intellij.database.run.actions.GridEditAction
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.fileEditor.FileDocumentManager

class CsvDataModeEditUsageListener : AnActionListener {
  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    if (isActionInCsvFileTableMode(event) && (action is GridEditAction || action is EditorAction)) {
      val project = event.project ?: return
      val grid = event.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
      val hookup = grid.getDataHookup() as? CsvDocumentDataHookUp ?: return
      val document = hookup.document
      val file = FileDocumentManager.getInstance().getFile(document) ?: return
      FileTypeUsageCounterCollector.triggerEdit(project, file)
    }
  }

  private fun isActionInCsvFileTableMode(event: AnActionEvent): Boolean {
    val grid = event.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return false
    return grid.getDataHookup() is CsvDocumentDataHookUp
  }
}
