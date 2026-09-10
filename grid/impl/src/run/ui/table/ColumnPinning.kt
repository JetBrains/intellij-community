package com.intellij.database.run.ui.table

import com.intellij.openapi.util.registry.Registry

/** Column pinning as a feature. The registry key is the rollback path, so everything checks it here. */
object ColumnPinning {
  @JvmStatic
  fun isEnabled(): Boolean = Registry.`is`("database.grid.column.pinning")
}
