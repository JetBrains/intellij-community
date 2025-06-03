package com.intellij.database.run.actions

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridUtilCore
import com.intellij.database.run.ui.DataGridRequestPlace
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.roots.ui.configuration.actions.AlignedIconWithTextAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.JBInsets
import java.awt.Insets
import javax.swing.JComponent

fun format(num: Long): @NlsSafe String {
  return String.format("%,d", num)
}

fun createCustomComponentForResultViewToolbar(
  action: AnAction,
  presentation: Presentation,
  place: String,
): JComponent {
  val c: ActionButtonWithText = object : ActionButtonWithText(action, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
    override fun getInsets(): Insets {
      return JBInsets(0, 0, 0, 0)
    }
  }
  return AlignedIconWithTextAction.align(c)
}

fun setPageSizeAndReload(pageSize: Int, grid: DataGrid) {
  val pageModel = grid.getDataHookup().getPageModel()
  pageModel.setPageSize(pageSize)

  val loader = grid.getDataHookup().getLoader()
  val source = GridRequestSource(DataGridRequestPlace(grid))
  if (GridUtilCore.isPageSizeUnlimited(pageSize)) loader.load(source, 0)
  else loader.reloadCurrentPage(source)
}
