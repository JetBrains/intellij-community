package com.intellij.database.datagrid

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.database.datagrid.nested.NestedTablesAware

@Deprecated(replaceWith = ReplaceWith("NestedTablesGridLoader"), message = "Use NestedTablesGridLoader instead")
interface NestedTablesAwareGridLoader {
  @Suppress("unused")
  fun selectNestedTable(path: List<NestedTableCellCoordinate>, nestedTable: NestedTable?)
  @Suppress("unused")
  fun isInStaticMode(): Boolean
}

interface NestedTablesGridLoader : NestedTablesAware<Unit> {
  fun isLoadAllowed(): Boolean
}
