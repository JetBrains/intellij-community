package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex

/** A cell of the grid in model indices, which name the same cell in both tables of the view. */
internal data class ModelCell(val row: ModelIndex<GridRow>, val column: ModelIndex<GridColumn>)
