package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.datagrid.MutationType
import com.intellij.database.run.ui.DataAccessType
import org.jetbrains.annotations.NotNull

class LocalFilterState(@NotNull grid: DataGrid, @Volatile var isEnabled: Boolean = true) {
  private val allowedValuesForColumn: MutableMap<ModelIndex<GridColumn>, MutableSet<Value>> = mutableMapOf()
  private val mutator = GridUtil.getDatabaseMutator(grid)
  private val alwaysShowMutationTypes = listOf(MutationType.DELETE, MutationType.INSERT)

  fun enableForColumn(column: ModelIndex<GridColumn>, value: Value) {
    allowedValuesForColumn.getOrPut(column) { mutableSetOf(value) }.add(value)
  }

  fun disableForColumn(column: ModelIndex<GridColumn>, value: Value) {
    (allowedValuesForColumn.getOrPut(column) { mutableSetOf() }).remove(value)
  }

  fun clearFilterForColumn(column: ModelIndex<GridColumn>) {
    allowedValuesForColumn.remove(column)
  }

  fun allowedValues(column: ModelIndex<GridColumn>): Set<Value> {
    return allowedValuesForColumn.getOrDefault(column, mutableSetOf())
  }

  fun columnFilterEnabled(column: ModelIndex<GridColumn>): Boolean {
    return allowedValuesForColumn[column]?.isNotEmpty() ?: false
  }

  fun reset() {
    allowedValuesForColumn.clear()
  }

  fun include(grid: DataGrid, rowIdx: ModelIndex<GridRow>, ignoreFilterForColumn: ModelIndex<GridColumn>? = null): Boolean {
    if (!isEnabled) {
      return true
    }

    if (mutator != null && mutator.getMutationType(rowIdx) in alwaysShowMutationTypes) {
      return true
    }

    return allowedValuesForColumn.all { allowedValues ->
      allowedValues.value.isEmpty() ||
      allowedValues.key == ignoreFilterForColumn ||
      allowedValues.value.contains(Value(null, GridUtil.getText(grid, rowIdx, allowedValues.key, DataAccessType.DATA_WITH_MUTATIONS)))
    }
  }

  fun shiftColumns(columnIndices: ModelIndexSet<GridColumn>, toOriginalIndex: (ModelIndex<GridColumn>) -> ModelIndex<GridColumn>) {
    val originalValues= allowedValuesForColumn.toMap()
    allowedValuesForColumn.clear()
    columnIndices.asIterable().forEach {
      val originalValue = originalValues[toOriginalIndex(it)]
      originalValue?.let { value -> allowedValuesForColumn[it] = value }
    }
  }

  class Value(val obj: Any?, val text: String) : Comparable<Value> {
    override fun compareTo(other: Value): Int {
      return text.compareTo(other.text)
    }

    override fun equals(other: Any?): Boolean {
      if (other !is Value) {
        return false
      }
      return text == other.text
    }

    override fun hashCode(): Int {
      return text.hashCode()
    }
  }

  interface Listener {
    fun onLocalFilterStateChanged() { /* by default, do nothing */ }
  }
}