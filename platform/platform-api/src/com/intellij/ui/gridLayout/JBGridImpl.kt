// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.gridLayout

import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

internal class JBGridImpl : JBGrid {

  override var resizableColumns = emptySet<Int>()
  override var resizableRows = emptySet<Int>()

  override var columnsDistance = emptyList<Int>()
  override var rowsDistance = emptyList<Int>()

  private val layoutData = JBLayoutData()
  private val cells = mutableListOf<JBCell>()

  fun register(component: JComponent, constraints: JBConstraints) {
    if (!isEmpty(constraints)) {
      throw JBGridException("Some cells are occupied already: $constraints")
    }

    cells.add(JBComponentCell(constraints, component))
  }

  fun registerSubGrid(constraints: JBConstraints): JBGrid {
    if (!isEmpty(constraints)) {
      throw JBGridException("Some cells are occupied already: $constraints")
    }

    val result = JBGridImpl()
    cells.add(JBGridCell(constraints, result))
    return result
  }

  fun unregister(component: JComponent) {
    val iterator = cells.iterator()
    for (cell in iterator) {
      if ((cell as? JBComponentCell)?.component == component) {
        iterator.remove()
        return
      }
    }

    throw JBGridException("Component has not been registered: $component")
  }

  fun getPreferredSize(): Dimension {
    calculateLayoutData()
    return layoutData.preferredSize
  }

  /**
   * Layouts components
   */
  fun layout(rect: Rectangle) {
    val columnsCoord = layoutData.columnsSizeCalculator.calculateCoords(rect.width, resizableColumns)
    val rowsCoord = layoutData.rowsSizeCalculator.calculateCoords(rect.height, resizableRows)

    layoutData.visibleCellsData.forEach { layoutCellData ->
      val cell = layoutCellData.cell
      val constraints = cell.constraints
      var x = columnsCoord[constraints.x]
      var y = rowsCoord[constraints.y]
      val nextColumn = constraints.x + constraints.width
      val nextRow = constraints.y + constraints.height
      val width = columnsCoord[nextColumn] - x - layoutCellData.gapWidth
      val height = rowsCoord[nextRow] - y - layoutCellData.gapHeight
      x += rect.x + constraints.gaps.left - constraints.visualPaddings.left
      y += rect.y + constraints.gaps.top - constraints.visualPaddings.top

      when (cell) {
        is JBComponentCell -> {
          layoutComponent(cell.component, layoutCellData, x, y, width, height)
        }
        is JBGridCell -> {
          (cell.content as JBGridImpl).layout(Rectangle(x, y, width, height))
        }
      }
    }
  }

  /**
   * Calculates all data in [layoutData], measures all components etc
   */
  fun calculateLayoutData() {
    layoutData.columnsSizeCalculator.reset()
    layoutData.rowsSizeCalculator.reset()

    calculateLayoutDataStep1()
    calculateLayoutDataStep2()
  }

  private fun calculateLayoutDataStep1() {
    layoutData.dimension = getDimension()
    val visibleCellsData = mutableListOf<LayoutCellData>()

    cells.forEach { cell ->
      when (cell) {
        is JBComponentCell -> {
          val component = cell.component
          if (component.isVisible) {
            visibleCellsData.add(LayoutCellData(cell, component.preferredSize))
          }
        }
        is JBGridCell -> {
          // todo visibility, visibleColumns and visibleRows
          val grid = cell.content as JBGridImpl
          grid.calculateLayoutData()
          visibleCellsData.add(LayoutCellData(cell, grid.layoutData.preferredSize))
        }
      }
    }

    layoutData.visibleCellsData = visibleCellsData
  }

  private fun calculateLayoutDataStep2() {
    fun isAfterColumnDistance(column: Int): Boolean {
      return column < columnsDistance.size &&
             column + 1 < layoutData.dimension.width // No distance after last column
    }

    fun isAfterRowDistance(row: Int): Boolean {
      return row < rowsDistance.size
             && row + 1 < layoutData.dimension.height // No distance after last row
    }

    layoutData.visibleCellsData.forEach { layoutCellData ->
      with(layoutCellData.cell.constraints) {
        val rightColumn = x + width - 1
        val bottomRow = y + height - 1
        layoutCellData.rightDistance = if (isAfterColumnDistance(rightColumn)) columnsDistance[rightColumn] else 0
        layoutCellData.bottomDistance = if (isAfterRowDistance(bottomRow)) rowsDistance[bottomRow] else 0

        layoutData.columnsSizeCalculator.addConstraint(x, width, layoutCellData.cellWidth)
        layoutData.rowsSizeCalculator.addConstraint(y, height, layoutCellData.cellHeight)
      }
    }
  }

  /**
   * Layouts component into provided rectangle. ALl kinds of gaps and distances are applied in it
   */
  private fun layoutComponent(component: JComponent, layoutCellData: LayoutCellData, x: Int, y: Int, width: Int, height: Int) {
    val constraints = layoutCellData.cell.constraints
    val resultWidth = if (constraints.horizontalAlign == HorizontalAlign.FILL)
      width
    else
      min(width, layoutCellData.preferredSize.width)
    val resultHeight = if (constraints.verticalAlign == VerticalAlign.FILL)
      height
    else
      min(height, layoutCellData.preferredSize.height)
    val resultX = x +
                  when (constraints.horizontalAlign) {
                    HorizontalAlign.LEFT -> 0
                    HorizontalAlign.CENTER -> (width - resultWidth) / 2
                    HorizontalAlign.RIGHT -> width - resultWidth
                    HorizontalAlign.FILL -> 0
                  }
    val resultY = y +
                  when (constraints.verticalAlign) {
                    VerticalAlign.TOP -> 0
                    VerticalAlign.CENTER -> (height - resultHeight) / 2
                    VerticalAlign.BOTTOM -> height - resultHeight
                    VerticalAlign.FILL -> 0
                  }

    component.setBounds(
      resultX, resultY,
      resultWidth, resultHeight
    )
  }

  /**
   * Maximum indexes of occupied cells including hidden components
   */
  private fun getDimension(): Dimension {
    var width = 0
    var height = 0
    cells.forEach { cell ->
      width = max(width, cell.constraints.x + cell.constraints.width)
      height = max(height, cell.constraints.y + cell.constraints.height)
    }
    return Dimension(width, height)
  }

  private fun isEmpty(constraints: JBConstraints): Boolean {
    cells.forEach { cell ->
      with(cell.constraints) {
        if (constraints.x + constraints.width > x &&
            x + width > constraints.x &&
            constraints.y + constraints.height > y &&
            y + height > constraints.y
        ) {
          return false
        }
      }
    }
    return true
  }
}

/**
 * Data that collected before layout/preferred size calculations
 */
private class JBLayoutData {

  var dimension = Dimension()
  var visibleCellsData = emptyList<LayoutCellData>()
  val columnsSizeCalculator = JBColumnsSizeCalculator()
  val rowsSizeCalculator = JBColumnsSizeCalculator()

  val preferredSize: Dimension
    get() = Dimension(columnsSizeCalculator.calculatePreferredSize(), rowsSizeCalculator.calculatePreferredSize())

}

private data class LayoutCellData(val cell: JBCell, val preferredSize: Dimension,
                                  var rightDistance: Int = 0, var bottomDistance: Int = 0) {

  val gapWidth: Int
    get() = cell.constraints.gaps.width - cell.constraints.visualPaddings.width + rightDistance

  val gapHeight: Int
    get() = cell.constraints.gaps.height - cell.constraints.visualPaddings.height + bottomDistance

  val cellWidth: Int
    get() = preferredSize.width + gapWidth

  val cellHeight: Int
    get() = preferredSize.height + gapHeight
}

private sealed class JBCell constructor(val constraints: JBConstraints)

private class JBComponentCell(constraints: JBConstraints, val component: JComponent) : JBCell(constraints)

private class JBGridCell(constraints: JBConstraints, val content: JBGrid) : JBCell(constraints)
