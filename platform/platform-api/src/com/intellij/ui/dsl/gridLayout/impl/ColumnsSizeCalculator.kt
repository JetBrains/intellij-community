// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout.impl

import com.intellij.ui.dsl.UiDslException
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max

/**
 * Calculates columns (or rows) sizes and position
 */
@ApiStatus.Internal
class ColumnsSizeCalculator {

  private val sizes = mutableMapOf<ColumnInfo, Int>()

  /**
   * [size] is a width constraint for columns with correspondent [x] and [width].
   * It includes gaps, visualPaddings, column gaps
   */
  fun addConstraint(x: Int, width: Int, size: Int) {
    val key = ColumnInfo(x, width)
    val existingSize = sizes[key] ?: 0
    sizes[key] = max(size, existingSize)
  }

  /**
   * Calculates columns coordinates with size limitations from [sizes]
   */
  fun calculateCoords(width: Int, resizableColumns: Set<Int>): Array<Int> {
    if (sizes.isEmpty()) {
      return arrayOf(0)
    }

    val dimension = getDimension()
    val visibleColumns = getVisibleColumns()
    val result = Array(dimension + 1) { 0 }
    var remainedSizes = sizes.toMap()
    val remainedResizableColumns = resizableColumns.intersect(visibleColumns).toMutableSet()

    // Calculate preferred sizes of columns one by one
    for (i in 0 until dimension) {
      if (visibleColumns.contains(i)) {
        val minCoords = calculateMinCoords(dimension - i, remainedSizes)
        val firstColumnMinWidth = minCoords[1]
        val sizesFirstColumn = mutableMapOf<ColumnInfo, Int>()
        val sizesWithoutFirstColumn = mutableMapOf<ColumnInfo, Int>()
        removeFirstColumn(remainedSizes, sizesFirstColumn, sizesWithoutFirstColumn, firstColumnMinWidth)
        val nextMinCoords = calculateMinCoords(dimension - i - 1, sizesWithoutFirstColumn)
        val firstColumnMaxWidth = minCoords.last() - nextMinCoords.last()
        val columnWidthCorrection = if (remainedResizableColumns.remove(i)) {
          (firstColumnMaxWidth - firstColumnMinWidth) / (remainedResizableColumns.size + 1)
        }
        else {
          0
        }
        for ((columnInfo, size) in sizesFirstColumn) {
          sizesWithoutFirstColumn[columnInfo] = max(sizesWithoutFirstColumn[columnInfo] ?: 0, size - columnWidthCorrection)
        }

        remainedSizes = sizesWithoutFirstColumn
        result[i + 1] = result[i] + firstColumnMinWidth + columnWidthCorrection
      }
      else {
        val sizesFirstColumn = mutableMapOf<ColumnInfo, Int>()
        val sizesWithoutFirstColumn = mutableMapOf<ColumnInfo, Int>()
        removeFirstColumn(remainedSizes, sizesFirstColumn, sizesWithoutFirstColumn, 0)

        if (sizesFirstColumn.isNotEmpty()) {
          throw UiDslException()
        }
        remainedSizes = sizesWithoutFirstColumn
        result[i + 1] = result[i]
      }
    }

    if (remainedSizes.isNotEmpty()) {
      throw UiDslException()
    }
    if (remainedResizableColumns.isNotEmpty()) {
      throw UiDslException()
    }

    resizeCoords(result, resizableColumns.filter { visibleColumns.contains(it) }.toSet(), width)

    return result
  }

  fun reset() {
    sizes.clear()
  }

  fun calculatePreferredSize(): Int {
    if (sizes.isEmpty()) {
      return 0
    }
    val minCoords = calculateMinCoords(getDimension(), sizes)
    return minCoords.last()
  }

  private fun removeFirstColumn(sizes: Map<ColumnInfo, Int>, sizesFirstColumn: MutableMap<ColumnInfo, Int>,
                                sizesWithoutFirstColumn: MutableMap<ColumnInfo, Int>, firstColumnMinWidth: Int) {
    for ((columnInfo, size) in sizes) {
      if (columnInfo.x == 0) {
        if (columnInfo.width > 1) {
          val index = ColumnInfo(0, columnInfo.width - 1)
          sizesFirstColumn[index] = max(sizesFirstColumn[index] ?: 0, size - firstColumnMinWidth)
        }
      }
      else {
        val index = ColumnInfo(columnInfo.x - 1, columnInfo.width)
        sizesWithoutFirstColumn[index] = max(sizesWithoutFirstColumn[index] ?: 0, size)
      }
    }
  }

  private fun getVisibleColumns(): Set<Int> {
    val result = mutableSetOf<Int>()
    for (columnInfo in sizes.keys) {
      result.addAll(columnInfo.x until columnInfo.x + columnInfo.width)
    }
    return result
  }

  private fun calculateMinCoords(dimension: Int, columnsInfo: Map<ColumnInfo, Int>): Array<Int> {
    val result = Array(dimension + 1) { 0 }
    val sortedSizes = columnsInfo.toSortedMap(Comparator.comparingInt(ColumnInfo::x).thenComparingInt(ColumnInfo::width))
    for ((columnInfo, size) in sortedSizes) {
      if (result[columnInfo.x] == 0) {
        // Fill coordinates for previous invisible columns
        val prevVisible = (columnInfo.x - 1 downTo 0).find { result[it] > 0 }
        if (prevVisible != null) {
          result.fill(result[prevVisible], prevVisible + 1, columnInfo.x + 1)
        }
      }

      val nextColumn = columnInfo.x + columnInfo.width
      result[nextColumn] = max(result[columnInfo.x] + size, result[nextColumn])
    }
    return result
  }

  /**
   * Resizes columns so that the grid occupies [width] (if there are resizable columns)
   * Extra size is distributed equally between [resizableColumns]
   */
  private fun resizeCoords(coordinates: Array<Int>, resizableColumns: Set<Int>, width: Int) {
    var extraSize = width - coordinates.last()

    if (extraSize == 0 || resizableColumns.isEmpty()) {
      return
    }

    var previousShift = 0
    // Filter out resizable columns that are out of scope
    // todo use isColumnVisible?
    var remainedResizableColumns = resizableColumns.count { it < coordinates.size - 1 }

    for (i in coordinates.indices) {
      coordinates[i] += previousShift

      if (i < coordinates.size - 1 && i in resizableColumns) {
        // Use such correction so exactly whole extra size is used (rounding could break other approaches)
        val correction = extraSize / remainedResizableColumns
        previousShift += correction
        extraSize -= correction
        remainedResizableColumns--
      }
    }
  }

  private fun getDimension(): Int {
    return sizes.keys.maxOf { it.x + it.width }
  }

  private data class ColumnInfo(val x: Int, val width: Int)
}
