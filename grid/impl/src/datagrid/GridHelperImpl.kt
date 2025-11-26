package com.intellij.database.datagrid

import com.intellij.database.connection.throwable.info.ErrorInfo
import com.intellij.database.data.types.BaseDataTypeConversion
import com.intellij.database.data.types.DataTypeConversion
import com.intellij.database.datagrid.GridMutator.ColumnsMutator
import com.intellij.database.dump.BaseGridHandler
import com.intellij.database.dump.DumpHandler
import com.intellij.database.dump.ExtractionHelper
import com.intellij.database.extractors.DataExtractorFactory
import com.intellij.database.extractors.ExtractionConfig
import com.intellij.database.extractors.ObjectFormatterMode
import com.intellij.database.run.actions.DumpSource
import com.intellij.database.run.actions.DumpSource.DataGridSource
import com.intellij.database.run.ui.DataAccessType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCodeFragment
import javax.swing.Icon

open class GridHelperImpl(
  override val properties: GridHelperPropertyProvider
) : GridHelper {
  @JvmOverloads
  constructor(insideNotebook: Boolean = IS_INSIDE_NOTEBOOK_DEFAULT_VALUE) : this(
    GridHelperPropertyProviderImpl(insideNotebook)
  )

  override fun createDataTypeConversionBuilder(): DataTypeConversion.Builder {
    return BaseDataTypeConversion.Builder()
  }

  override val defaultMode: ObjectFormatterMode get() = ObjectFormatterMode.SQL_SCRIPT

  override fun canEditTogether(
    grid: CoreGrid<GridRow, GridColumn>,
    columns: MutableList<GridColumn>,
  ): Boolean = true

  override fun findUniqueColumn(
    grid: CoreGrid<GridRow, GridColumn>,
    columns: MutableList<GridColumn>,
  ): GridColumn? = null

  override fun getColumnIcon(grid: CoreGrid<GridRow, GridColumn>, column: GridColumn, forDisplay: Boolean): Icon? {
    return null
  }

  override fun getVirtualFile(grid: CoreGrid<GridRow, GridColumn>): VirtualFile? {
    return GridUtil.getVirtualFile(grid)
  }

  override fun applyFix(project: Project, fix: ErrorInfo.Fix, editor: Any?) {
  }

  override fun getUnambiguousColumnNames(grid: CoreGrid<GridRow, GridColumn>): List<String> {
    return emptyList()
  }

  override fun canAddRow(grid: CoreGrid<GridRow, GridColumn>): Boolean {
    return true
  }

  override fun getTableName(grid: CoreGrid<GridRow, GridColumn>): String? {
    return null
  }

  override fun getNameForDump(source: DataGrid): String? {
    return GridUtil.getEditorTabName(source)
  }

  override fun getQueryText(source: DataGrid): String {
    return ""
  }

  override fun isDatabaseHookUp(grid: DataGrid): Boolean {
    return false
  }

  override fun createDumpSource(grid: DataGrid, e: AnActionEvent): DumpSource<*>? {
    return DataGridSource(grid)
  }

  override fun createDumpHandler(
    source: DumpSource<*>,
    manager: ExtractionHelper,
    factory: DataExtractorFactory,
    config: ExtractionConfig
  ): DumpHandler<*> {
    val gridSource = source as DataGridSource
    val grid = gridSource.grid
    return object : BaseGridHandler(grid.getProject(), grid, gridSource.nameProvider, manager, factory, config) {
      override fun createProducer(grid: DataGrid, index: Int): DataProducer {
        val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
        return IdentityDataProducerImpl(DataConsumer.Composite(),
                                        model.getColumns(),
                                        ArrayList<GridRow?>(model.getRows()),
                                        0,
                                        0)
      }
    }
  }

  override fun isMixedTypeColumns(grid: CoreGrid<GridRow, GridColumn>): Boolean {
    return true
  }

  override fun isSortingApplicable(): Boolean {
    return true
  }

  override fun hasTargetForEditing(grid: CoreGrid<GridRow, GridColumn>): Boolean {
    return true
  }

  override fun canMutateColumns(grid: CoreGrid<GridRow, GridColumn>): Boolean {
    return grid.isEditable() && grid.isReady() &&
           grid.getDataHookup() is DocumentDataHookUp &&
           grid.getDataHookup().getMutator() is ColumnsMutator<GridRow?, GridColumn?> && grid.getDataModel(
      DataAccessType.DATA_WITH_MUTATIONS).getRowCount() != 0 // todo: check maybe it's okay to add columns to empty file
  }

  override fun isEditable(grid: CoreGrid<GridRow, GridColumn>): Boolean {
    return true
  }

  override fun setFilterText(grid: CoreGrid<GridRow, GridColumn>, text: String, caretPosition: Int) {
    grid.setFilterText(text, caretPosition)
  }

  override fun getCellLanguage(
    grid: CoreGrid<GridRow, GridColumn>,
    row: ModelIndex<GridRow>,
    column: ModelIndex<GridColumn>
  ): Language? {
    return null
  }

  override fun createCellCodeFragment(
    text: String,
    project: Project,
    grid: CoreGrid<GridRow, GridColumn>,
    row: ModelIndex<GridRow>,
    column: ModelIndex<GridColumn>
  ): PsiCodeFragment? {
    return null
  }
}
