package com.intellij.database.datagrid

import com.intellij.database.datagrid.nested.NestedTablesAware

interface NestedTablesGridLoader : NestedTablesAware<Unit> {
  fun isLoadAllowed(): Boolean
}
