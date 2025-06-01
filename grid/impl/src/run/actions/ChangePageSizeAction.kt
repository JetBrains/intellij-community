package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.GridPagingModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class ChangePageSizeAction(private val myPageSize: Int) : DumbAwareAction(if (myPageSize == GridPagingModel.UNLIMITED_PAGE_SIZE) DataGridBundle.message("action.ChangePageSize.text.all") else format(myPageSize.toLong()),
                                                                          if (myPageSize == GridPagingModel.UNLIMITED_PAGE_SIZE)
                                                                            DataGridBundle.message("action.ChangePageSize.description.all")
                                                                          else
                                                                            DataGridBundle.message("action.ChangePageSize.description.some", format(myPageSize.toLong())),
                                                                          null) {
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
