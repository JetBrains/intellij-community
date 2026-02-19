@file:JvmName("FormatterConfigCache")
package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.extractors.BinaryDisplayType
import com.intellij.database.extractors.DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.settings.DataGridSettings
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.util.CachedValueProvider


fun DataGrid.getCacheValueProvider(): CachedValueProvider<Map<ModelIndex<GridColumn>, DatabaseDisplayObjectFormatterConfig>> {
  return CachedValueProvider<Map<ModelIndex<GridColumn>, DatabaseDisplayObjectFormatterConfig>> {
    val allowedTypes = GridUtil.getAllowedTypes(this) // depend on settings
    val settings = GridUtil.getSettings(this) // depend on settings
    val result = this.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).columnIndices.asIterable().toMap { columnIdx ->
      createFormatterConfig(this, columnIdx, allowedTypes, settings)
    } // depend on the column model and all content
    CachedValueProvider.Result.create(result, this.modificationTracker, settings?.modificationTracker
                                                                        ?: ModificationTracker.NEVER_CHANGED)
  }
}

private fun createFormatterConfig(
  grid: DataGrid,
  column: ModelIndex<GridColumn>,
  allowedTypes: Set<BinaryDisplayType>,
  settings: DataGridSettings?,
): DatabaseDisplayObjectFormatterConfig {
  val isModeDetectedAutomatically = grid.getPureDisplayType(column) == BinaryDisplayType.DETECT
  return DatabaseDisplayObjectFormatterConfig(
    grid.getDisplayType(column),
    isModeDetectedAutomatically,
    allowedTypes,
    settings
  )
}