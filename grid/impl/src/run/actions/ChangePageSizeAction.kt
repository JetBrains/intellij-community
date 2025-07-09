package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridPagingModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls

class ChangePageSizeActionNew(private val myPageSize: Int, isDefault: Boolean = false) :
  ToggleAction(
    /* text = */ formatPageSize(myPageSize, isDefault, DataGridBundle.message("action.ChangePageSize.text.all")),
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    e.presentation.setEnabledAndVisible(grid != null)
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return false
    return getCurrentPageSize(grid) == myPageSize
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY) ?: return
      setPageSizeAndReload(myPageSize, grid)
    }
  }

  private fun getCurrentPageSize(grid: DataGrid?): Int {
    return grid?.dataHookup?.pageModel?.pageSize ?: GridPagingModel.UNLIMITED_PAGE_SIZE
  }
}

private fun formatPageSize(pageSize: Int, isDefault: Boolean, defaultText: @Nls String): @Nls String {
  val formattedPageSize = format(pageSize.toLong())
  val pageSizeText = if (pageSize == GridPagingModel.UNLIMITED_PAGE_SIZE) defaultText else formattedPageSize
  val builder = HtmlBuilder().append(pageSizeText).append(" ")
  if (isDefault) {
    builder.append(
      HtmlChunk.text(DataGridBundle.message("action.ChangePageSize.text.default"))
        .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())))
    )
  }

  return builder.wrapWith(HtmlChunk.html()).toString()
}