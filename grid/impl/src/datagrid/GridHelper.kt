package com.intellij.database.datagrid

import com.intellij.database.connection.throwable.info.ErrorInfo
import com.intellij.database.data.types.DataTypeConversion
import com.intellij.database.dump.DumpHandler
import com.intellij.database.dump.ExtractionHelper
import com.intellij.database.extractors.DataExtractor
import com.intellij.database.extractors.DataExtractorFactories
import com.intellij.database.extractors.DataExtractorFactory
import com.intellij.database.extractors.ExtractionConfig
import com.intellij.database.extractors.GridExtractorsUtilCore
import com.intellij.database.extractors.ObjectFormatterMode
import com.intellij.database.extractors.builder
import com.intellij.database.run.actions.DumpSource
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.grid.DefaultGridColumnLayout
import com.intellij.database.run.ui.grid.GridRowComparator
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.database.util.Out
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.containers.JBIterable
import java.util.function.Consumer
import javax.swing.Icon

interface GridHelper : CoreGridHelper {
  fun createDataTypeConversionBuilder(): DataTypeConversion.Builder

  val defaultMode: ObjectFormatterMode

  val properties: GridHelperPropertyProvider

  fun canEditTogether(grid: CoreGrid<GridRow, GridColumn>, columns: MutableList<GridColumn>): Boolean

  fun canSortTogether(
    grid: CoreGrid<GridRow, GridColumn>,
    oldOrdering: List<ModelIndex<GridColumn>>,
    newColumns: List<ModelIndex<GridColumn>>?
  ): Boolean {
    return true
  }

  fun findUniqueColumn(grid: CoreGrid<GridRow, GridColumn>, columns: MutableList<GridColumn>): GridColumn?

  fun getColumnIcon(grid: CoreGrid<GridRow, GridColumn>, column: GridColumn, forDisplay: Boolean): Icon?

  fun getVirtualFile(grid: CoreGrid<GridRow, GridColumn>): VirtualFile?

  fun getChildrenFromModel(grid: CoreGrid<GridRow, GridColumn>): JBIterable<TreeElement> {
    return JBIterable.empty()
  }

  fun getLocationString(element: PsiElement?): String? {
    return null
  }

  fun setFilterSortHighlighter(grid: CoreGrid<GridRow, GridColumn>, editor: Editor) {
  }

  fun updateFilterSortPSI(grid: CoreGrid<GridRow, GridColumn>) {
  }

  fun applyFix(project: Project, fix: ErrorInfo.Fix, editor: Any?)

  fun getUnambiguousColumnNames(grid: CoreGrid<GridRow, GridColumn>): List<String>

  fun canAddRow(grid: CoreGrid<GridRow, GridColumn>): Boolean

  fun hasTargetForEditing(grid: CoreGrid<GridRow, GridColumn>): Boolean // DBE-12001

  fun getTableName(grid: CoreGrid<GridRow, GridColumn>): String?

  fun getNameForDump(source: DataGrid): String?

  fun getQueryText(source: DataGrid): String?

  fun isDatabaseHookUp(grid: DataGrid): Boolean

  fun createDumpSource(grid: DataGrid, e: AnActionEvent): DumpSource<*>?

  fun createDumpHandler(
    source: DumpSource<*>,
    manager: ExtractionHelper,
    factory: DataExtractorFactory,
    config: ExtractionConfig
  ): DumpHandler<*>

  fun isDumpEnabled(source: DumpSource<*>): Boolean {
    return true
  }

  fun syncExtractorsInNotebook(grid: DataGrid, factory: DataExtractorFactory) {
    findAllGridsInFile(grid).forEach(Consumer { g: DataGrid? ->
      val f = g!!.getUserData(DataExtractorFactories.GRID_DATA_EXTRACTOR_FACTORY_KEY)
      if (f == null || factory.id != f.id) {
        DataExtractorFactories.setExtractorFactory(g, factory)
      }
    })
  }

  fun isLoadWholeTableWhenPaginationIsOff(grid: DataGrid): Boolean {
    return false
  }

  fun createColumnLayout(resultView: TableResultView, grid: DataGrid): GridColumnLayout<GridRow, GridColumn> {
    return DefaultGridColumnLayout(resultView, grid)
  }

  fun getColumnTooltipHtml(grid: CoreGrid<GridRow, GridColumn>, columnIdx: ModelIndex<GridColumn>): @NlsContexts.Tooltip String? {
    return null
  }

  @NlsSafe
  fun getDatabaseSystemName(grid: CoreGrid<GridRow, GridColumn>): @NlsSafe String? {
    return null
  }

  fun isEditable(grid: CoreGrid<GridRow, GridColumn>): Boolean

  fun createComparator(column: GridColumn): GridRowComparator? {
    return GridRowComparator.create(column)
  }

  fun extractValues(
    dataGrid: DataGrid,
    extractor: DataExtractor,
    out: Out,
    selection: Boolean,
    transpositionAllowed: Boolean
  ): Out {
    val model = dataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
    val columns = (if (selection) dataGrid.getSelectionModel().getSelectedColumns() else model.getColumnIndices()).asArray()
    val rows = if (selection) GridUtil.getSelectedGridRows(dataGrid) else model.getRows()
    val transposed = transpositionAllowed && dataGrid.getResultView().isTransposed()
    val config = builder()
      .setTransposed(transposed)
      .setAddGeneratedColumns(!DataExtractorFactories.getSkipGeneratedColumns(dataGrid))
      .setAddComputedColumns(!DataExtractorFactories.getSkipComputedColumns(dataGrid))
      .build()
    GridExtractorsUtilCore.extract(out, config,
                                   model.getAllColumnsForExtraction(*columns),
                                   extractor, rows, *columns)

    return out
  }

  fun extractValuesForCopy(
    dataGrid: DataGrid,
    extractor: DataExtractor,
    out: Out,
    selection: Boolean,
    transpositionAllowed: Boolean
  ): Out {
    return extractValues(dataGrid, extractor, out, selection, transpositionAllowed)
  }

  fun isColumnContainNestedTables(gridModel: GridModel<GridRow, GridColumn>?, column: GridColumn): Boolean {
    return false
  }

  companion object {
    @JvmStatic
    fun get(grid: CoreGrid<*, *>): GridHelper {
      return GRID_HELPER_KEY.get(grid)!!
    }

    @JvmStatic
    fun supportsTableStatistics(grid: DataGrid?): Boolean {
      if (grid == null) return false

      val tableResultView = grid.getResultView()
      if (tableResultView is TableResultView) {
        return tableResultView.statisticsHeader != null
      }
      else {
        return false
      }
    }

    @JvmStatic
    fun set(grid: CoreGrid<*, *>, helper: GridHelper) {
      GRID_HELPER_KEY.set(grid, helper)
    }

    @JvmStatic
    val GRID_HELPER_KEY: Key<GridHelper?> = Key<GridHelper?>("GRID_HELPER_KEY")
  }
}
