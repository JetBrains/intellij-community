/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe

import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.columns.Column
import java.awt.Dimension

abstract class DataFrame {
  abstract val dim: Dimension

  abstract fun getColumns(): List<Column<*>>
  abstract operator fun get(columnName: String): Column<*>
  abstract operator fun get(columnIndex: Int): Column<*>
  abstract fun has(columnName: String): Boolean

  abstract fun sortBy(columnName: String, sortDescending: Boolean = false)

  abstract fun groupBy(columnName: String): DataFrame

  val columnsCount: Int
    get() {
      return getColumns().size
    }

  val rowsCount: Int
    get() {
      return if (getColumns().isEmpty()) 0 else get(0).size
    }
}
