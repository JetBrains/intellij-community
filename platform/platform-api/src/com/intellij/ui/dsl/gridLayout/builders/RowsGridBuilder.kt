// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout.builders

import com.intellij.ui.dsl.gridLayout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.math.max

private const val GRID_EMPTY = -1

/**
 * Builds grid layout row by row
 */
@ApiStatus.Experimental
class RowsGridBuilder(private val panel: JComponent, grid: Grid? = null) {

  private val layout = panel.layout as GridLayout

  val grid = grid ?: layout.rootGrid

  var columnsCount: Int = 0
    private set

  val resizableColumns: MutableSet<Int> by this.grid::resizableColumns

  private var defaultHorizontalAlign = HorizontalAlign.LEFT
  private var defaultVerticalAlign = VerticalAlign.CENTER
  private var defaultBaselineAlign = false

  private var x = 0
  private var y = GRID_EMPTY

  fun columnsGaps(value: List<UnscaledGapsX>): RowsGridBuilder {
    grid.columnsGaps.clear()
    grid.columnsGaps.addAll(value)
    return this
  }

  /**
   * Starts new row. Can be omitted for the first and after last rows
   */
  fun row(rowGaps: UnscaledGapsY = UnscaledGapsY.EMPTY, resizable: Boolean = false): RowsGridBuilder {
    x = 0
    y++

    setRowGaps(rowGaps)

    if (resizable) {
      resizableRow()
    }

    return this
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use the overloaded function with UnscaledGaps in parameters")
  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
           verticalAlign: VerticalAlign = defaultVerticalAlign,
           baselineAlign: Boolean = defaultBaselineAlign,
           resizableColumn: Boolean = false,
           gaps: Gaps = Gaps.EMPTY,
           visualPaddings: Gaps = Gaps.EMPTY,
           widthGroup: String? = null,
           componentHelper: ComponentHelper? = null): RowsGridBuilder {
    return cell(component, width, horizontalAlign, verticalAlign, baselineAlign, resizableColumn,
                gaps.toUnscaled(), visualPaddings.toUnscaled(), widthGroup, componentHelper)
  }

  // todo needed only while migration Gaps to UnscaledGaps. Should be removed later
  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
           verticalAlign: VerticalAlign = defaultVerticalAlign,
           baselineAlign: Boolean = defaultBaselineAlign,
           resizableColumn: Boolean = false,
           widthGroup: String? = null,
           componentHelper: ComponentHelper? = null): RowsGridBuilder {
    return cell(component, width, horizontalAlign, verticalAlign, baselineAlign, resizableColumn,
                UnscaledGaps.EMPTY, UnscaledGaps.EMPTY, widthGroup, componentHelper)
  }

  fun cell(component: JComponent,
           width: Int = 1,
           horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
           verticalAlign: VerticalAlign = defaultVerticalAlign,
           baselineAlign: Boolean = defaultBaselineAlign,
           resizableColumn: Boolean = false,
           gaps: UnscaledGaps = UnscaledGaps.EMPTY,
           visualPaddings: UnscaledGaps = UnscaledGaps.EMPTY,
           widthGroup: String? = null,
           componentHelper: ComponentHelper? = null): RowsGridBuilder {
    if (y == GRID_EMPTY) {
      y = 0
    }
    if (resizableColumn) {
      addResizableColumn()
    }

    val constraints = Constraints(grid, x, y, width = width, horizontalAlign = horizontalAlign,
                                  verticalAlign = verticalAlign, baselineAlign = baselineAlign,
                                  gaps = gaps, visualPaddings = visualPaddings, widthGroup = widthGroup,
                                  componentHelper = componentHelper)
    panel.add(component, constraints)
    return skip(width)
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use the overloaded function with UnscaledGaps in parameters")
  fun constraints(width: Int = 1,
                  horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                  verticalAlign: VerticalAlign = defaultVerticalAlign,
                  baselineAlign: Boolean = defaultBaselineAlign,
                  gaps: Gaps = Gaps.EMPTY,
                  visualPaddings: Gaps = Gaps.EMPTY,
                  widthGroup: String? = null,
                  componentHelper: ComponentHelper? = null): Constraints {
    return constraints(width, horizontalAlign, verticalAlign, baselineAlign,
                       gaps.toUnscaled(), visualPaddings.toUnscaled(), widthGroup, componentHelper)
  }

  // todo needed only while migration Gaps to UnscaledGaps. Should be removed later
  fun constraints(width: Int = 1,
                  horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                  verticalAlign: VerticalAlign = defaultVerticalAlign,
                  baselineAlign: Boolean = defaultBaselineAlign,
                  widthGroup: String? = null,
                  componentHelper: ComponentHelper? = null): Constraints {
    return constraints(width, horizontalAlign, verticalAlign, baselineAlign,
                       UnscaledGaps.EMPTY, UnscaledGaps.EMPTY, widthGroup, componentHelper)
  }

  fun constraints(width: Int = 1,
                  horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                  verticalAlign: VerticalAlign = defaultVerticalAlign,
                  baselineAlign: Boolean = defaultBaselineAlign,
                  gaps: UnscaledGaps = UnscaledGaps.EMPTY,
                  visualPaddings: UnscaledGaps = UnscaledGaps.EMPTY,
                  widthGroup: String? = null,
                  componentHelper: ComponentHelper? = null): Constraints {
    if (y == GRID_EMPTY) {
      y = 0
    }
    val result = Constraints(grid, x, y,
                             width = width, horizontalAlign = horizontalAlign,
                             verticalAlign = verticalAlign, baselineAlign = baselineAlign,
                             gaps = gaps, visualPaddings = visualPaddings,
                             widthGroup = widthGroup,
                             componentHelper = componentHelper)
    skip(width)
    return result
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use the overloaded function with UnscaledGaps in parameters")
  fun subGrid(width: Int = 1,
              horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
              verticalAlign: VerticalAlign = defaultVerticalAlign,
              baselineAlign: Boolean = defaultBaselineAlign,
              resizableColumn: Boolean = false,
              gaps: Gaps = Gaps.EMPTY,
              visualPaddings: Gaps = Gaps.EMPTY): Grid {
    return subGrid(width, horizontalAlign, verticalAlign, baselineAlign, resizableColumn, gaps.toUnscaled(), visualPaddings.toUnscaled())
  }

  fun subGrid(width: Int = 1,
              horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
              verticalAlign: VerticalAlign = defaultVerticalAlign,
              baselineAlign: Boolean = defaultBaselineAlign,
              resizableColumn: Boolean = false,
              gaps: UnscaledGaps = UnscaledGaps.EMPTY,
              visualPaddings: UnscaledGaps = UnscaledGaps.EMPTY): Grid {
    startFirstRow()
    if (resizableColumn) {
      addResizableColumn()
    }

    val constraints = Constraints(grid, x, y, width = width, horizontalAlign = horizontalAlign,
      verticalAlign = verticalAlign, baselineAlign = baselineAlign,
      gaps = gaps, visualPaddings = visualPaddings)
    skip(width)
    return layout.addLayoutSubGrid(constraints)
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use the overloaded function with UnscaledGaps in parameters")
  fun subGridBuilder(width: Int = 1,
                     horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                     verticalAlign: VerticalAlign = defaultVerticalAlign,
                     baselineAlign: Boolean = defaultBaselineAlign,
                     resizableColumn: Boolean = false,
                     gaps: Gaps = Gaps.EMPTY,
                     visualPaddings: Gaps = Gaps.EMPTY): RowsGridBuilder {
    return subGridBuilder(width, horizontalAlign, verticalAlign, baselineAlign,
                          resizableColumn, gaps.toUnscaled(), visualPaddings.toUnscaled())
  }


  // todo needed only while migration Gaps to UnscaledGaps. Should be removed later
  fun subGridBuilder(width: Int = 1,
                     horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                     verticalAlign: VerticalAlign = defaultVerticalAlign,
                     baselineAlign: Boolean = defaultBaselineAlign,
                     resizableColumn: Boolean = false): RowsGridBuilder {
    return subGridBuilder(width, horizontalAlign, verticalAlign, baselineAlign, resizableColumn, UnscaledGaps.EMPTY, UnscaledGaps.EMPTY)
  }

  fun subGridBuilder(width: Int = 1,
                     horizontalAlign: HorizontalAlign = defaultHorizontalAlign,
                     verticalAlign: VerticalAlign = defaultVerticalAlign,
                     baselineAlign: Boolean = defaultBaselineAlign,
                     resizableColumn: Boolean = false,
                     gaps: UnscaledGaps = UnscaledGaps.EMPTY,
                     visualPaddings: UnscaledGaps = UnscaledGaps.EMPTY): RowsGridBuilder {
    return RowsGridBuilder(panel, subGrid(width, horizontalAlign, verticalAlign, baselineAlign, resizableColumn, gaps, visualPaddings))
      .defaultHorizontalAlign(defaultHorizontalAlign)
      .defaultVerticalAlign(defaultVerticalAlign)
      .defaultBaselineAlign(defaultBaselineAlign)
  }

  /**
   * Skips [count] cells in current row
   */
  fun skip(count: Int = 1): RowsGridBuilder {
    x += count
    columnsCount = max(columnsCount, x)
    return this
  }

  fun setRowGaps(rowGaps: UnscaledGapsY) {
    startFirstRow()

    while (grid.rowsGaps.size <= y) {
      grid.rowsGaps.add(UnscaledGapsY.EMPTY)
    }
    grid.rowsGaps[y] = rowGaps
  }

  private fun defaultHorizontalAlign(defaultHorizontalAlign: HorizontalAlign): RowsGridBuilder {
    this.defaultHorizontalAlign = defaultHorizontalAlign
    return this
  }

  fun defaultVerticalAlign(defaultVerticalAlign: VerticalAlign): RowsGridBuilder {
    this.defaultVerticalAlign = defaultVerticalAlign
    return this
  }

  fun defaultBaselineAlign(defaultBaselineAlign: Boolean): RowsGridBuilder {
    this.defaultBaselineAlign = defaultBaselineAlign
    return this
  }

  /**
   * Marks current row as a resizable one
   */
  fun resizableRow(): RowsGridBuilder {
    startFirstRow()
    grid.resizableRows += y
    return this
  }

  fun addResizableColumn() {
    grid.resizableColumns += x
  }

  private fun startFirstRow() {
    if (y == GRID_EMPTY) {
      y = 0
    }
  }
}
