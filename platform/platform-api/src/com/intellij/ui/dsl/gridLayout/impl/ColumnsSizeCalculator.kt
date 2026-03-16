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

  private val sizes = mutableMapOf<ColumnInfo, SizeInfo>()

  /**
   * [minSize] and [prefSize] are width constraints for columns with correspondent [x] and [width].
   * It includes gaps, visualPaddings, column gaps
   */
  fun addConstraint(x: Int, width: Int, minSize: Int, prefSize: Int) {
    val key = ColumnInfo(x, width)
    val existingSizeInfo = sizes[key]
    sizes[key] = max(existingSizeInfo, minSize, prefSize)
  }

  /**
   * Calculates columns coordinates with size limitations from [sizes]
   */
  fun calculateCoords(width: Int, resizableColumns: Set<Int>, respectMinimumSize: Boolean): Array<Int> {
    if (sizes.isEmpty()) {
      return arrayOf(0)
    }

    val resizableVisibleColumns = resizableColumns.intersect(getVisibleColumns())
    val preferredSizeWidths = calculateWidths(resizableVisibleColumns, sizes.mapValues { it.value.prefSize })
    val minimumSizeWidths = if (respectMinimumSize)
      calculateWidths(resizableVisibleColumns, mapSizesToMinimumSizes(resizableVisibleColumns))
    else null

    return createCoordsFromWidths(calculateResizedWidths(minimumSizeWidths, preferredSizeWidths, resizableVisibleColumns, width))
  }

  // todo make static later
  private fun calculateWidths(resizableVisibleColumns: Set<Int>, sizes: Map<ColumnInfo, Int>): Array<Int> {
    val dimension = getDimension()
    val visibleColumns = getVisibleColumns()
    val result = Array(dimension) { 0 }
    var remainedSizes = sizes
    val remainedResizableColumns = resizableVisibleColumns.toMutableSet()

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
        result[i] = firstColumnMinWidth + columnWidthCorrection
      }
      else {
        val sizesFirstColumn = mutableMapOf<ColumnInfo, Int>()
        val sizesWithoutFirstColumn = mutableMapOf<ColumnInfo, Int>()
        removeFirstColumn(remainedSizes, sizesFirstColumn, sizesWithoutFirstColumn, 0)

        if (sizesFirstColumn.isNotEmpty()) {
          throw UiDslException()
        }
        remainedSizes = sizesWithoutFirstColumn
        result[i] = 0
      }
    }

    if (remainedSizes.isNotEmpty()) {
      throw UiDslException()
    }
    if (remainedResizableColumns.isNotEmpty()) {
      throw UiDslException()
    }

    return result
  }

  fun reset() {
    sizes.clear()
  }

  /**
   * Calculates minimum possible size with provided by [addConstraint] constraints
   */
  fun calculateMinimumSize(resizableColumns: Set<Int>): Int {
    if (sizes.isEmpty()) {
      return 0
    }

    val resizableVisibleColumns = resizableColumns.intersect(getVisibleColumns())
    val minCoords = calculateMinCoords(getDimension(), mapSizesToMinimumSizes(resizableVisibleColumns))
    return minCoords.last()
  }

  /**
   * Calculates preferred size with provided by [addConstraint] constraints
   */
  fun calculatePreferredSize(): Int {
    if (sizes.isEmpty()) {
      return 0
    }
    val minCoords = calculateMinCoords(getDimension(), sizes.mapValues { it.value.prefSize })
    return minCoords.last()
  }

  /**
   * For minimum size calculation non-resizable columns use preferred sizes (as the fixed final size), resizable - minimum sizes
   */
  private fun mapSizesToMinimumSizes(resizableVisibleColumns: Set<Int>): Map<ColumnInfo, Int> {
    return sizes.mapValues {
      var resizable = false
      for (i in 0..<it.key.width) {
        if (resizableVisibleColumns.contains(it.key.x + i)) {
          resizable = true
          break
        }
      }

      if (resizable) it.value.minSize else it.value.prefSize
    }
  }

  private fun removeFirstColumn(
    sizes: Map<ColumnInfo, Int>, sizesFirstColumn: MutableMap<ColumnInfo, Int>,
    sizesWithoutFirstColumn: MutableMap<ColumnInfo, Int>, firstColumnMinWidth: Int,
  ) {
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
   * Extra size is distributed equally between [resizableVisibleColumns]
   */
  private fun calculateResizedWidths(
    minimumSizeWidths: Array<Int>?,
    preferredSizeWidths: Array<Int>,
    resizableVisibleColumns: Set<Int>,
    width: Int,
  ): Array<Int> {
    if (width == preferredSizeWidths.sum() || resizableVisibleColumns.isEmpty()) {
      return preferredSizeWidths
    }

    // Calculate columns based on preferred sizes
    val result = preferredSizeWidths.clone()

    // Filter out resizable columns that are out of scope
    val remainedResizableColumns = resizableVisibleColumns.filter { it < preferredSizeWidths.size }.toMutableList()

    while (!remainedResizableColumns.isEmpty()) {
      var remainedResizableColumnsCount = remainedResizableColumns.size
      var extraSize = width - result.sum() // can be negative
      var minimumSizeExceededIndex: Int? = null

      for ((index, column) in remainedResizableColumns.withIndex()) {
        // Use such correction so exactly the whole extra size is used (rounding could break other approaches)
        val correction = extraSize / remainedResizableColumnsCount
        remainedResizableColumnsCount--
        extraSize -= correction

        result[column] += correction
        if (minimumSizeWidths != null && result[column] < minimumSizeWidths[column]) {
          result[column] = minimumSizeWidths[column]
          minimumSizeExceededIndex = index

          break
        }
      }

      if (minimumSizeExceededIndex == null) {
        break
      }

      // Restore affected columns
      for ((index, column) in remainedResizableColumns.withIndex()) {
        if (index >= minimumSizeExceededIndex) {
          break
        }
        result[column] = preferredSizeWidths[column]
      }

      remainedResizableColumns.removeAt(minimumSizeExceededIndex)

      // Redistribute extra size over remained resizable columns
    }

    return result
  }

  private fun createCoordsFromWidths(widths: Array<Int>): Array<Int> {
    val result = Array(widths.size + 1) { 0 }
    for ((i, width) in widths.withIndex()) {
      result[i + 1] = result[i] + width
    }
    return result
  }

  private fun getDimension(): Int {
    return sizes.keys.maxOf { it.x + it.width }
  }
}

private data class ColumnInfo(val x: Int, val width: Int)

private data class SizeInfo(val minSize: Int, val prefSize: Int)

private fun max(sizeInfo: SizeInfo?, minSize: Int, prefSize: Int): SizeInfo {
  return SizeInfo(max(sizeInfo?.minSize ?: 0, minSize), max(sizeInfo?.prefSize ?: 0, prefSize))
}
