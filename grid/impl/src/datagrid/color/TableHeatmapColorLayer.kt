package com.intellij.database.datagrid.color

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridModel
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.datagrid.mutating.ColumnDescriptor
import com.intellij.database.extractors.ObjectFormatterUtil
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import java.awt.Color
import kotlin.math.roundToInt

/**
 * Adds and controls table heatmap-styled coloring.
 *
 * Usage: {code}TableHeatmapColorLayer.installOn(dataGrid){code}
 */
class TableHeatmapColorLayer private constructor(private val dataGrid: DataGrid, private val tableResultView: TableResultView, private val useOldColors: Boolean) : ColorLayer, GridModel.Listener<GridRow, GridColumn>, Disposable {

  enum class ColoringMode(val title: String) {
    OFF(DataGridBundle.message("datagrid.coloring.mode.off")),
    SEQUENTIAL(DataGridBundle.message("datagrid.coloring.mode.sequential")),
    DIVERGING(DataGridBundle.message("datagrid.coloring.mode.diverging"));

    companion object {
      fun getByName(name: String?) : ColoringMode = entries.firstOrNull { it.name == name } ?: DIVERGING
    }
  }

  private class ColumnRange(val min: Double, val max: Double)

  private val columns = mutableMapOf<ModelIndex<GridColumn>, ColumnRange>()
  private var globalMax = -Double.MAX_VALUE
  private var globalMin = Double.MAX_VALUE

  var coloringMode: ColoringMode = getColoringMode()
    set(value) {
      if (field != value) {
        field = value
        setColoringMode(value)
        if (useOldColors) {
          tableResultView.showHorizontalLines = value != ColoringMode.OFF
        }
        tableResultView.repaint()
      }
    }

  /** When true, the coloring separate for every numeric column, when false, the coloring is global. */
  var perColumn : Boolean = isPerColumnColoringEnabled()
    set(value) {
      if (field != value) {
        field = value
        tableResultView.repaint()
      }
    }

  var colorBooleanColumns: Boolean = isColorBooleanColumnsEnabled()
    set(value) {
      if (field != value) {
        field = value
        tableResultView.repaint()
      }
    }

  init {
    dataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).addListener(this, this)
    updateRanges()
  }

  override fun dispose(): Unit = Unit

  override fun columnsAdded(columns: ModelIndexSet<GridColumn>?): Unit = Unit
  override fun columnsRemoved(columns: ModelIndexSet<GridColumn>?): Unit = Unit
  override fun rowsAdded(rows: ModelIndexSet<GridRow>?): Unit = Unit
  override fun rowsRemoved(rows: ModelIndexSet<GridRow>?): Unit = Unit
  override fun cellsUpdated(
    rows: ModelIndexSet<GridRow>?,
    columns: ModelIndexSet<GridColumn>?,
    place: GridRequestSource.RequestPlace?,
  ): Unit = Unit

  override fun afterLastRowAdded(): Unit = updateRanges()

  private fun updateRanges() {
    val dataModel = dataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)

    if (dataModel.rowCount == 0 || dataModel.columnCount == 0) {
      return
    }

    dataModel.columnIndices.asIterable().forEach { columnIndex ->
      val column = dataModel.getColumn(columnIndex)
      val firstRow = dataModel.rowIndices.first()

      // ColumnDescriptor.Attribute.HIGHLIGHTED currently is the only normal way do extract index column from coloring.
      if (column == null ||
          column.attributes.contains(ColumnDescriptor.Attribute.HIGHLIGHTED) ||
          !ObjectFormatterUtil.isNumericCell(dataGrid, firstRow, columnIndex)) {
        return@forEach
      }

      var min = Double.MAX_VALUE
      var max = -Double.MAX_VALUE
      dataModel.rowIndices.asIterable().forEach { rowIndex ->
        val value = dataModel.getValueAt(rowIndex, columnIndex).toString().toDoubleOrNull()
        if (value != null) {
          if (min > value) min = value
          if (max < value) max = value
        }
      }

      if (max > globalMax) globalMax = max
      if (min < globalMin) globalMin = min

      if (min != Double.MAX_VALUE && max != -Double.MAX_VALUE) {
        columns[columnIndex] = ColumnRange(min, max)
      }
    }
  }

  private fun getColor(x: Double, minX: Double, maxX: Double): Color? {
    if (minX == maxX) return null // Protection from the situation with one-row table or column of same values.

    val p = (x - minX) / (maxX - minX)
    return when (coloringMode) { // Currently, this is white to blue.
      ColoringMode.SEQUENTIAL -> {
        //if (JBColor.isBright())
        //  Color.getHSBColor(240 / 360f, p.toFloat() * 0.6f, 1f)
        //else
        //  Color.getHSBColor(240 / 360f, 220 / 360f, p.toFloat() * 0.7f)
        if (JBColor.isBright())
          getSimpleGradientLight(useOldColors).getColor(p.toFloat())
        else
          getSimpleGradientDark(useOldColors).getColor(p.toFloat())
      }

      // Multiple color gradient orange - blue - red.
      ColoringMode.DIVERGING -> {
        if (JBColor.isBright())
          getMultipleGradientLight(useOldColors).getColor(p.toFloat())
        else
          getMultipleGradientDark(useOldColors).getColor(p.toFloat())
      }

      // Should not be called, but we will return our legacy colors for now.
      else -> {
        @Suppress("UseJBColor") if (JBColor.isBright()) Color((255 * p).roundToInt(), 0, (255 * (1 - p)).roundToInt(), 255).brighter()
        else Color((255 * p).roundToInt(), 0, (255 * (1 - p)).roundToInt(), 255)
      }
    }
  }

  override fun getCellBackground(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>, grid: DataGrid, color: Color?): Color? {
    if (coloringMode == ColoringMode.OFF) return null

    val dataModel = dataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)

    if (colorBooleanColumns && ObjectFormatterUtil.isBooleanCell(grid, row, column)) {
      return if (dataModel.getValueAt(row, column).toString().lowercase() == "true") {
        getColor(0.6, 0.0, 1.0)
      }
      else {
        getColor(0.0, 0.0, 1.0)
      }
    }

    val columnRange = columns[column] ?: return null

    val doubleValue = dataModel.getValueAt(row, column).toString().toDoubleOrNull() ?: return null

    return if (perColumn) getColor(doubleValue, columnRange.min, columnRange.max)
    else getColor(doubleValue, globalMin, globalMax)
  }

  override fun getRowHeaderBackground(row: ModelIndex<GridRow>, grid: DataGrid, color: Color?): Color? = null

  override fun getColumnHeaderBackground(column: ModelIndex<GridColumn>, grid: DataGrid, color: Color?): Color? = null

  override fun getPriority(): Int = 0

  companion object {
    @Suppress("UseJBColor")
    val simpleGradientLightOld: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 1f), arrayOf(Color(93, 143, 244),
                                                     Color(212, 226, 255)))
    }

    @Suppress("UseJBColor")
    val simpleGradientLight: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 1f), arrayOf(Color(54, 150, 80),
                                                     Color(242, 252, 243)))
    }

    fun getSimpleGradientLight(useOldColors: Boolean): MultipleGradient = if (useOldColors) simpleGradientLightOld else simpleGradientLight

    @Suppress("UseJBColor")
    val simpleGradientDarkOld: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 1f), arrayOf(Color(54, 106, 207),
                                                     Color(37, 50, 77)))
    }

    @Suppress("UseJBColor")
    val simpleGradientDark: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 1f), arrayOf(Color(87, 150, 92),
                                                     Color(37, 54, 39)))
    }

    fun getSimpleGradientDark(useOldColors: Boolean): MultipleGradient = if (useOldColors) simpleGradientDarkOld else simpleGradientDark

    // Test 3-colors gradient for diverging coloring.
    @Suppress("UseJBColor")
    val multipleGradientLightOld: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 0.5f, 1f), arrayOf(Color(88, 140, 243),
                                                           Color.WHITE,
                                                           Color(228, 108, 120)))
    }

    @Suppress("UseJBColor")
    val multipleGradientLight: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 0.5f, 1f), arrayOf(Color(54, 150, 80),
                                                           Color.WHITE,
                                                           Color(228, 108, 120)))
    }

    fun getMultipleGradientLight(useOldColors: Boolean): MultipleGradient = if (useOldColors) multipleGradientLightOld else multipleGradientLight

    @Suppress("UseJBColor")
    val multipleGradientDarkOld: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 0.5f, 1f), arrayOf(Color(54, 106, 207, ),
                                                           Color(30, 31, 34),
                                                           Color(189, 87, 87)))
    }

    @Suppress("UseJBColor")
    val multipleGradientDark: MultipleGradient by lazy {
      MultipleGradient(floatArrayOf(0f, 0.5f, 1f), arrayOf(Color(87, 150, 92),
                                                           Color(30, 31, 34),
                                                           Color(189, 87, 87)))
    }

    fun getMultipleGradientDark(useOldColors: Boolean): MultipleGradient = if (useOldColors) multipleGradientDarkOld else multipleGradientDark

    val HEATMAP_OLD_COLORS : Key<Boolean> = Key.create<Boolean>("HEATMAP_OLD_COLORS")

    private const val COLOR_BOOLEAN_COLUMNS = "table.view.heatmap.color.boolean.columns"
    private const val PER_COLUMN_COLORING = "table.view.heatmap.color.per.columns"
    private const val COLORING_MODE = "table.view.heatmap.coloring.mode"

    fun getColoringMode(): ColoringMode = ColoringMode.getByName(PropertiesComponent.getInstance().getValue(COLORING_MODE))
    fun setColoringMode(mode: ColoringMode): Unit = PropertiesComponent.getInstance().setValue(COLORING_MODE, mode.name)

    fun isPerColumnColoringEnabled(): Boolean = PropertiesComponent.getInstance().getBoolean(PER_COLUMN_COLORING, false)
    fun setPerColumnColoringEnabled(value: Boolean): Unit = PropertiesComponent.getInstance().setValue(PER_COLUMN_COLORING, value)

    fun isColorBooleanColumnsEnabled(): Boolean = PropertiesComponent.getInstance().getBoolean(COLOR_BOOLEAN_COLUMNS, true)
    fun setColorBooleanColumnsEnabled(value: Boolean): Unit = PropertiesComponent.getInstance().setValue(COLOR_BOOLEAN_COLUMNS, value, true)

    fun installOn(dataGrid: DataGrid): TableHeatmapColorLayer? {
      val gridColorModel = dataGrid.colorModel as? GridColorModelImpl ?: return null
      val tableResultView = dataGrid.resultView as? TableResultView ?: return null
      val useOldColors = dataGrid.getUserData(HEATMAP_OLD_COLORS) == true
      val tableHeatmapColorLayer = TableHeatmapColorLayer(dataGrid, tableResultView, useOldColors)
      Disposer.register(dataGrid, tableHeatmapColorLayer)
      gridColorModel.addLayer(tableHeatmapColorLayer)
      tableResultView.repaint()

      if (useOldColors) {
        tableResultView.showHorizontalLines = true
      }

      return tableHeatmapColorLayer
    }
  }
}