// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.gridLayout.builders

import com.intellij.ui.gridLayout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Builds grid layout row by row
 */
@ApiStatus.Experimental
class RowsGridBuilder(private val panel: JComponent, private val grid: JBGrid = (panel.layout as JBGridLayout).rootGrid) {

  private val GRID_EMPTY = -1
  private var x = 0
  private var y = GRID_EMPTY

  fun resizableColumns(value: Set<Int>): RowsGridBuilder {
    grid.resizableColumns = value
    return this
  }

  fun columnsDistance(value: List<Int>): RowsGridBuilder {
    grid.columnsDistance = value
    return this
  }

  /**
   * Starts new row. Can be omitted for the first row
   *
   * @param distance distance between previous row and new one. Not used for the first row
   * @param resizable true if new row is resizable
   */
  fun row(distance: Int = 0, resizable: Boolean = false): RowsGridBuilder {
    if (distance < 0) {
      throw JBGridException("Invalid parameter: distance = $distance")
    }

    // Update rowsDistance
    if (y != GRID_EMPTY && distance > 0) {
      val rowsDistance = grid.rowsDistance.toMutableList()
      while (rowsDistance.size <= y) {
        rowsDistance.add(0)
      }
      rowsDistance[y] = distance
      grid.rowsDistance = rowsDistance
    }

    x = 0
    y++

    if (resizable) {
      val resizableRows = grid.resizableRows.toMutableSet()
      resizableRows.add(y)
      grid.resizableRows = resizableRows
    }

    return this
  }

  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
           verticalAlign: VerticalAlign = VerticalAlign.TOP,
           gaps: Gaps = Gaps.EMPTY,
           visualPaddings: Gaps = Gaps.EMPTY): RowsGridBuilder {
    if (y == GRID_EMPTY) {
      y = 0
    }

    val constraints = JBConstraints(grid, x, y, width = width, verticalAlign = verticalAlign, horizontalAlign = horizontalAlign,
                                    gaps = gaps, visualPaddings = visualPaddings)
    panel.add(component, constraints)
    return skipCell()
  }

  fun skipCell(): RowsGridBuilder {
    x++
    return this
  }
}
