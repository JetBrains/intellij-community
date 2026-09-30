package com.intellij.database.datagrid

import org.jetbrains.annotations.ApiStatus

/**
 * A property of a column that the column list can search on.
 *
 * Only a database grid reports a trait. A CSV file, a notebook table and a Parquet file report none,
 * so the column list hides the trait conditions there.
 */
@ApiStatus.Experimental
enum class GridColumnTrait {
  PRIMARY_KEY,
  FOREIGN_KEY,
  AUTO_INCREMENT,
}
