package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.*
import com.intellij.database.run.ui.FloatingPagingManager
import com.intellij.database.run.ui.FloatingPagingManager.Companion.adjustAction
import com.intellij.database.settings.DataGridSettings
import com.intellij.database.util.DataGridUIUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.UI
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Component
import java.util.*
import javax.swing.JComponent

private val DEFAULT_PAGE_SIZES = mutableListOf(10, 100, 500, 1000)
private val PAGE_SIZE_KEY = Key<Int?>("DATA_GRID_PAGE_SIZE_KEY")
private val SHOW_COUNT_ALL_ACTION_KEY = Key<Boolean?>("DATA_GRID_SHOW_COUNT_ALL_ACTION_KEY")

class ChangePageSizeActionGroup : DefaultActionGroup(), CustomComponentAction, DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  init {
    isPopup = true
    setActions(DEFAULT_PAGE_SIZES, false, GridPagingModel.UNLIMITED_PAGE_SIZE)
  }

  private fun setActions(sizes: MutableList<Int>, isSinglePage: Boolean, defaultPageSize: Int) {
    removeAll()

    if (isSinglePage) {
      add(MyCountRowsAction())
    }

    addSeparator(DataGridBundle.message("separator.page.size"))
    for (pageSize in sizes) {
      add(ChangePageSizeActionNew(pageSize, pageSize == defaultPageSize))
    }
    add(ChangePageSizeActionNew(GridPagingModel.UNLIMITED_PAGE_SIZE, GridPagingModel.UNLIMITED_PAGE_SIZE == defaultPageSize))
    add(SetCustomPageSizeAction())
    add(Separator())
    add(SetDefaultPageSizeAction())
  }

  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    if (
      grid == null ||
      grid.getDataHookup().let { dataHookUp ->
        dataHookUp is DocumentDataHookUp ||
        (dataHookUp.pageModel as? NestedTableGridPagingModel<GridRow?, GridColumn?>)?.isStatic == true
      }
    ) {
      e.presentation.setEnabledAndVisible(false)
      return
    }

    if (adjustAction(e) == FloatingPagingManager.AdjustmentResult.HIDDEN) {
      return
    }

    val state = getActionState(grid)
    if (GridUtil.hidePageActions(grid, e.place)) {
      e.presentation.setVisible(false)
    }
    else {
      e.presentation.setVisible(true)
      val updateEdtJob = grid.coroutineScope.launch(Dispatchers.UI) {
        updatePresentation(state, e.presentation, GridUtil.getSettings(grid))
      }

      // We wait for it because we need an updated presentation when the function returns,
      // it's the contract of AnAction.update() method
      runBlockingMaybeCancellable {
        updateEdtJob.join()
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val component: Component? = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
    val popup: JBPopup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.dataContext, null, true, null)
    if (component == null) {
      DataGridUIUtil.showPopup(popup, null, e)
      return
    }
    popup.showUnderneathOf(component)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return createCustomComponentForResultViewToolbar(this, presentation, place)
  }

  @RequiresEdt
  private fun updatePresentation(state: ChangePageSizeActionState, presentation: Presentation, settings: DataGridSettings?) {
    val oldState = getActionState(presentation)
    if (oldState == state) return

    presentation.setText(state.text)
    presentation.setDescription(state.description)
    presentation.setEnabled(state.enabled)
    presentation.putClientProperty<Int?>(PAGE_SIZE_KEY, state.pageSize)
    presentation.putClientProperty<Boolean?>(SHOW_COUNT_ALL_ACTION_KEY, state.showCountAllAction)

    val component = presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
    if (component != null) {
      component.setToolTipText(state.tooltip)
      component.repaint()
    }

    val pageSizes: MutableList<Int> = ArrayList<Int>(DEFAULT_PAGE_SIZES)
    pageSizes.add(GridUtilCore.getPageSize(settings))
    if (state.pageSize > 0) {
      pageSizes.add(state.pageSize * 2)
      val halfSize = state.pageSize / 2
      if (halfSize > 0) pageSizes.add(halfSize)
      pageSizes.add(state.pageSize)
    }
    ContainerUtil.removeDuplicates(pageSizes)
    ContainerUtil.sort(pageSizes)

    setActions(pageSizes, state.showCountAllAction, state.defaultPageSize)
  }

}

private fun getActionState(presentation: Presentation): ChangePageSizeActionState {
  val component = presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)

  val text = presentation.text
  val description = presentation.description
  val tooltip = component?.toolTipText
  val loading = presentation.isEnabled
  var pageSize = presentation.getClientProperty<Int?>(PAGE_SIZE_KEY)
  if (pageSize == null) pageSize = GridPagingModel.UNSET_PAGE_SIZE
  var showCountAllAction = presentation.getClientProperty<Boolean?>(SHOW_COUNT_ALL_ACTION_KEY)
  if (showCountAllAction == null) showCountAllAction = false

  return ChangePageSizeActionState(text, description, tooltip, loading, pageSize, showCountAllAction)
}

private fun getActionState(grid: DataGrid): ChangePageSizeActionState {
  val pageModel = grid.getDataHookup().getPageModel()

  val pageStartIdx = pageModel.getPageStart()
  val pageEndIdx = pageModel.getPageEnd()
  val totalRowCount = pageModel.getTotalRowCount()

  val rowsWereDeleted = totalRowCount < pageEndIdx
  val isSinglePage = pageModel.isFirstPage() && pageModel.isLastPage() && !rowsWereDeleted

  val text = when {
    isSinglePage -> {
      val rowLabel = if (totalRowCount == 1L)
        DataGridBundle.message("action.Console.TableResult.ChangePageSize.row")
      else
        DataGridBundle.message("action.Console.TableResult.ChangePageSize.rows")
      format(totalRowCount) + " " + rowLabel
    }
    pageEndIdx == 0 -> {
      "0 " + DataGridBundle.message("action.Console.TableResult.ChangePageSize.rows")
    }
    else -> {
      format(pageStartIdx.toLong()) + "-" + format(pageEndIdx.toLong())
    }
  }

  val querying = grid.getDataHookup().getBusyCount() > 0
  val enabled = !querying && grid.isReady()

  var description = DataGridBundle.message("group.Console.TableResult.ChangePageSize.description")
  var tooltip = DataGridBundle.message("group.Console.TableResult.ChangePageSize.description")
  if (!enabled) {
    val unavailableText = if (querying) DataGridBundle.message("action.Console.TableResult.ChangePageSize.querying") else ""
    description = unavailableText
    tooltip = unavailableText
  }

  val showCountRowsAction = isSinglePage && pageModel.isTotalRowCountUpdateable() && !querying && grid.isReady()
  return ChangePageSizeActionState(text, description, tooltip, enabled, pageModel.getPageSize(), showCountRowsAction, GridHelper.get(grid).getDefaultPageSize())
}

private fun updateIsTotalRowCountUpdateable(grid: DataGrid) {
  grid.getDataHookup().getLoader().updateIsTotalRowCountUpdateable()
}

private class ChangePageSizeActionState(
  val text: @NlsActions.ActionText String?,
  val description: @NlsActions.ActionDescription String?,
  val tooltip: @NlsContexts.Tooltip String?,
  val enabled: Boolean,
  val pageSize: Int,
  val showCountAllAction: Boolean,
  val defaultPageSize: Int = GridPagingModel.UNLIMITED_PAGE_SIZE,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val state = other as ChangePageSizeActionState
    return enabled == state.enabled &&
           pageSize == state.pageSize &&
           text == state.text &&
           description == state.description &&
           tooltip == state.tooltip
  }

  override fun hashCode(): Int {
    return Objects.hash(text, description, tooltip, enabled, pageSize)
  }
}

private class MyCountRowsAction : DumbAwareAction(
  DataGridBundle.message("action.CountRows.text"),
  DataGridBundle.message("action.CountRows.description"),
  null
) {
  override fun actionPerformed(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    if (grid == null) {
      e.presentation.setEnabledAndVisible(false)
      return
    }
    updateIsTotalRowCountUpdateable(grid)
    val pageModel = grid.getDataHookup().getPageModel()
    if (!pageModel.isTotalRowCountUpdateable()) return
    CountRowsAction.countRows(grid)
    updateIsTotalRowCountUpdateable(grid)
  }
}
