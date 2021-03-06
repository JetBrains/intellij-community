// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.panels

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBInsets
import java.awt.*
import javax.swing.SwingConstants.*

/**
 * This class is intended to lay out added components in a grid, which cells have the same size.
 * It allows to align the last row to the LEFT, CENTER, or RIGHT if it is shorter than specified.
 * Also it allows to specify a gap between columns or rows. The gap will be scaled automatically.
 */
open class RowGridLayout(
  private val columns: Int,
  private val rows: Int,
  private val gap: Int,
  private val alignment: Int = CENTER
) : LayoutManager {
  init {
    require(columns > 0 || rows > 0) { "unsupported columns and rows: $columns x $rows" }
    require(alignment == LEFT || alignment == RIGHT || alignment == CENTER) { "unsupported alignment: $alignment" }
  }

  override fun addLayoutComponent(name: String, comp: Component) = Unit
  override fun removeLayoutComponent(comp: Component) = Unit

  override fun layoutContainer(parent: Container) = synchronized(parent.treeLock) {
    val bounds = Rectangle(parent.width, parent.height)
    JBInsets.removeFrom(bounds, parent.insets)
    val count = parent.componentCount
    val grid = getGridSize(count)
    if (grid.width > 0 && grid.height > 0) {
      val gap = if (gap <= 0) 0 else JBUI.scale(gap)
      val width = ((bounds.width + gap) / grid.width - gap).coerceAtLeast(0)
      val height = ((bounds.height + gap) / grid.height - gap).coerceAtLeast(0)
      var x = bounds.x - width - gap
      var y = bounds.y - height - gap
      for (i in 0 until count) {
        if (i % grid.width != 0) {
          x += width + gap
        }
        else {
          y += height + gap
          x = bounds.x
          if (alignment != LEFT) {
            var space = i + grid.width - count
            if (space > 0) {
              space *= width + gap
              if (alignment != RIGHT) space /= 2
              x += space
            }
          }
        }
        parent.getComponent(i).setBounds(x, y, width, height)
      }
    }
  }

  override fun preferredLayoutSize(parent: Container) = getSize(parent) { it.preferredSize }
  override fun minimumLayoutSize(parent: Container) = getSize(parent) { it.minimumSize }

  private fun getSize(parent: Container, function: (Component) -> Dimension) = Dimension().also { size ->
    synchronized(parent.treeLock) {
      JBInsets.addTo(size, parent.insets)
      val components = parent.components
      val grid = getGridSize(components.size)
      if (grid.width > 0 && grid.height > 0) {
        val cell = getCellSize(components.map { function(it) })
        if (cell.width > 0 && cell.height > 0) {
          val gap = if (gap <= 0) 0 else JBUI.scale(gap)
          size.width += (gap + cell.width) * grid.width - gap
          size.height += (gap + cell.height) * grid.height - gap
        }
      }
    }
  }

  private fun getGrid(count: Int, size: Int) = when {
    count <= 0 || size <= 0 -> 0
    count % size == 0 -> count / size
    else -> 1 + count / size
  }

  private fun getGridSize(count: Int) = when {
    columns <= 0 -> rows.coerceAtMost(count).let { Dimension(getGrid(count, it), it) }
    rows <= 0 -> columns.coerceAtMost(count).let { Dimension(it, getGrid(count, it)) }
    columns * rows < count -> Dimension(columns, getGrid(count, columns))
    else -> Dimension(columns, rows)
  }

  /**
   * @param sizes a list of suggested sizes of all components
   * @return the size for every cell in the grid
   */
  protected open fun getCellSize(sizes: List<Dimension>) = Dimension().also { size ->
    if (sizes.isNotEmpty()) {
      size.width = sizes.maxOf { it.width }
      size.height = sizes.maxOf { it.height }
    }
  }
}
