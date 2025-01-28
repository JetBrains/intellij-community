package com.intellij.database.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys.DATA_GRID_KEY
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.extractors.DataExtractorFactories
import com.intellij.database.extractors.DataExtractorFactories.getDisplayName
import com.intellij.database.extractors.ExtractorsHelper
import com.intellij.ide.actions.CopyAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.text.StringUtil

class GridCopyAction : CopyAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val grid = e.getData(DATA_GRID_KEY)
    if (grid == null) {
      e.presentation.isVisible = false
      return
    }
    if (!shouldShowCopyExtractor(grid)) return
    val scripts = DataExtractorFactories.getExtractorScripts(ExtractorsHelper.getInstance(grid), null)
    val name = getDisplayName(DataExtractorFactories.getExtractorFactory(grid, GridUtil::suggestPlugin), scripts)
    val trimmedName = StringUtil.firstLast(name, 24)
    e.presentation.text = DataGridBundle.message("action.Console.TableResult.Copy.with.extractor.text", trimmedName)
  }

}

fun shouldShowCopyExtractor(grid: DataGrid?): Boolean {
  val selectionModel = grid?.selectionModel
  return selectionModel != null && !(selectionModel.selectedRowCount == 1 && selectionModel.selectedColumnCount == 1)
}
