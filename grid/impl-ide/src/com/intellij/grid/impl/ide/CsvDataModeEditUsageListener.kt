// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grid.impl.ide

import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.CsvDocumentDataHookUp
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.run.actions.GridEditAction
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.util.Collections
import java.util.WeakHashMap

internal class CsvDataModeEditUsageListener : AnActionListener, DataGridListener {
  private val gridsInEditSession: MutableSet<DataGrid> = Collections.newSetFromMap(WeakHashMap())

  override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
    if (isActionInCsvFileTableMode(event) && action is GridEditAction) {
      val project = event.project ?: return
      val grid = event.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
      val hookup = grid.getDataHookup() as? CsvDocumentDataHookUp ?: return
      val file = FileDocumentManager.getInstance().getFile(hookup.document) ?: return
      FileTypeUsageCounterCollector.triggerEdit(project, file)
    }
  }

  override fun onValueEdited(dataGrid: DataGrid, value: Any?) {
    val hookup = dataGrid.getDataHookup() as? CsvDocumentDataHookUp ?: return
    if (gridsInEditSession.add(dataGrid)) {
      val file = FileDocumentManager.getInstance().getFile(hookup.document) ?: return
      FileTypeUsageCounterCollector.triggerEdit(hookup.project, file)
    }
  }

  override fun onSelectionChanged(dataGrid: DataGrid) {
    gridsInEditSession.remove(dataGrid)
  }

  private fun isActionInCsvFileTableMode(event: AnActionEvent): Boolean {
    val grid = event.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return false
    return grid.getDataHookup() is CsvDocumentDataHookUp
  }
}