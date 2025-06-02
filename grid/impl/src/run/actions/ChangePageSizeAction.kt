package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.GridPagingModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.Nls

class ChangePageSizeAction(private val myPageSize: Int) :
  DumbAwareAction(
    /* text = */ formatPageSize(myPageSize, DataGridBundle.message("action.ChangePageSize.text.all")),
    /* description = */ formatPageSize(myPageSize, DataGridBundle.message("action.ChangePageSize.description.all")),
    /* icon = */ null
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    e.presentation.setEnabledAndVisible(grid != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    if (grid == null) return
    setPageSizeAndReload(myPageSize, grid)
  }
}

private fun formatPageSize(pageSize: Int, defaultText: @Nls String): @Nls String {
  return if (pageSize == GridPagingModel.UNLIMITED_PAGE_SIZE) {
    defaultText
  } else {
    format(pageSize.toLong())
  }
}
