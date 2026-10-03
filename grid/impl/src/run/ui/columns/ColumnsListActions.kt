package com.intellij.database.run.ui.columns

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridPomTarget
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.ide.CopyProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import java.awt.datatransfer.StringSelection

/** Provides platform actions and a data context for the column list. */
internal class ColumnsListActions(
  private val grid: DataGrid,
  private val controller: ColumnsListController,
  val selectedColumns: () -> ModelIndexSet<GridColumn>,
  private val copyTargets: () -> List<ColumnsListItem>,
  private val canMoveRow: (Int) -> Boolean,
  private val moveRow: (Int) -> Unit,
) {
  val commands get() = controller.commands
  val copyTargetCount: Int get() = copyTargets().size
  val originalOrder: Boolean get() = controller.isOriginalOrder()
  val copyProvider: CopyProvider = object : CopyProvider {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    override fun performCopy(dataContext: DataContext) = copyNames()
    override fun isCopyEnabled(dataContext: DataContext): Boolean = copyTargetCount > 0
    override fun isCopyVisible(dataContext: DataContext): Boolean = true
  }

  fun uiDataSnapshot(sink: DataSink) {
    sink[DATA_KEY] = this
    val elements = columnElements(selectedColumns().asIterable().toList()).toTypedArray()
    val file = GridUtil.getVirtualFile(grid)
    sink[CommonDataKeys.PROJECT] = grid.project
    // GridUtil.getDataGrid reads this key, so an action that asks for the grid finds one.
    sink[DatabaseDataKeys.DATA_GRID_KEY] = grid
    // The popup shows a list and not text. An action that needs an editor must stay disabled.
    sink.setNull(CommonDataKeys.EDITOR)
    sink.setNull(PlatformDataKeys.COPY_PROVIDER)
    sink.setNull(PlatformDataKeys.PASTE_PROVIDER)
    sink.setNull(PlatformDataKeys.DELETE_ELEMENT_PROVIDER)
    sink.lazy(CommonDataKeys.PSI_FILE) {
      file?.let { PsiManager.getInstance(grid.project).findFile(it) }
    }
    if (elements.isEmpty()) {
      sink.setNull(PlatformCoreDataKeys.SELECTED_ITEM)
      sink.setNull(PlatformCoreDataKeys.SELECTED_ITEMS)
      sink.setNull(CommonDataKeys.PSI_ELEMENT)
      sink.setNull(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY)
      sink.setNull(CommonDataKeys.NAVIGATABLE)
      sink.setNull(CommonDataKeys.NAVIGATABLE_ARRAY)
    }
    else {
      sink[PlatformCoreDataKeys.SELECTED_ITEM] = elements.first()
      sink[PlatformCoreDataKeys.SELECTED_ITEMS] = elements
      // Keep the popup selection ahead of inherited lazy PSI providers from the grid.
      sink[CommonDataKeys.PSI_ELEMENT] = elements.first()
      sink[PlatformCoreDataKeys.PSI_ELEMENT_ARRAY] = elements
      sink[CommonDataKeys.NAVIGATABLE_ARRAY] = elements.filterIsInstance<Navigatable>().toTypedArray()
    }
  }

  /** The columns under the selection, as the platform sees a column. */
  private fun columnElements(columns: List<ModelIndex<GridColumn>>): List<PsiElement> =
    columns.filter { it.isValid(grid) }
      .map { DataGridPomTarget.wrapColumn(grid.project, grid, it) }

  /** Copies the names, separated by a comma, the way the Copy Column Name action of the header does. */
  fun copyNames() {
    val names = copyTargets().map { it.name }
    if (names.isEmpty()) return
    CopyPasteManager.getInstance().setContents(StringSelection(names.joinToString(",")))
  }

  /** Copies one column per line, with a tab between the name and the type. */
  fun copyNamesAndTypes() {
    val rows = copyTargets()
    if (rows.isEmpty()) return
    val text = rows.joinToString("\n") { item ->
      if (item.typeText == null) item.name else "${item.name}\t${item.typeText}"
    }
    CopyPasteManager.getInstance().setContents(StringSelection(text))
  }

  fun pinSelected() = controller.pin(selectedColumns())

  fun unpinSelected() = controller.unpin(selectedColumns())

  fun pinUpToHere() {
    val column = selectedColumns().asIterable().singleOrNull() ?: return
    controller.pinUpToHere(column)
  }

  fun unpinAll() = controller.unpinAll()

  fun restoreOriginalOrder() = controller.restoreOriginalOrder()

  private abstract class ListAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  /** Moves the selected row, so that a reorder needs no pointer. The drag does the same work. */
  private inner class MoveRowAction(private val delta: Int) : ListAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.text = DataGridBundle.message(
        if (delta < 0) "action.Console.TableResult.ColumnsList.Popup.MoveRowUp"
        else "action.Console.TableResult.ColumnsList.Popup.MoveRowDown"
      )
      e.presentation.isEnabledAndVisible = canMoveRow(delta)
    }

    override fun actionPerformed(e: AnActionEvent) {
      moveRow(delta)
    }
  }

  fun moveRowAction(delta: Int): AnAction = MoveRowAction(delta)

  companion object {
    val DATA_KEY: DataKey<ColumnsListActions> = DataKey.create("database.columnsList.actions")
  }
}
