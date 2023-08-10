// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

/**
 * Grid representation of [GridLayout] root container or cells sub-grids
 */
interface Grid {

  /**
   * Set of columns that fill available extra space in container
   */
  val resizableColumns: MutableSet<Int>

  /**
   * Set of rows that fill available extra space in container
   */
  val resizableRows: MutableSet<Int>

  /**
   * Gaps around columns. Used only when column is visible
   */
  val columnsGaps: MutableList<UnscaledGapsX>

  /**
   * Gaps around rows. Used only when row is visible
   */
  val rowsGaps: MutableList<UnscaledGapsY>
}
