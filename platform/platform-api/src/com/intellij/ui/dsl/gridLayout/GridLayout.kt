// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.gridLayout

import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.checkComponent
import com.intellij.ui.dsl.checkConstraints
import com.intellij.ui.dsl.gridLayout.impl.GridImpl
import com.intellij.ui.dsl.gridLayout.impl.SizeConstrainsData
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import javax.swing.JComponent

/**
 * Layout manager represented as a table, where some cells can be merged in one cell (the resulting cell occupies several columns and rows)
 * and every cell (or merged cells) can contain a sub-table inside. [Constraints] specifies all possible settings for every cell.
 * Root grid [rootGrid] and all sub-grids have own columns and rows settings placed in [Grid]
 */
@ApiStatus.Experimental
class GridLayout : LayoutManager2 {

  /**
   * Root grid of layout
   */
  val rootGrid: Grid
    get() = _rootGrid

  private val _rootGrid = GridImpl()

  /**
   * Forces layout manager to respect the minimum size of components:
   * * Components don't exceed the minimum size (`false` - old behavior: resizable rows/columns are squeezed, and left/right components may overlap)
   * * [minimumLayoutSize] calculates real minimum size (`false` - old behavior: preferred size is used as minimum size)
   * * When parent has the preferred size: all resizable children should have preferred size
   * * While resizing the parent, resizable children are resized based on their preferred size
   * * When parent has the minimum size: all resizable children should have minimum size
   *
   * todo - Remove this option once the renderers are migrated from [GridLayout] and use it as the default behavior
   */
  @ApiStatus.Internal
  var respectMinimumSize: Boolean = false

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    val checkedConstraints = checkConstraints(constraints)
    val checkedComponent = checkComponent(comp)

    (checkedConstraints.grid as GridImpl).register(checkedComponent, checkedConstraints)
  }

  fun setComponentConstrains(comp: JComponent, constraints: Constraints) {
    (constraints.grid as GridImpl).setConstraints(comp, constraints)
  }

  /**
   * Creates a sub grid in the specified cell
   */
  fun addLayoutSubGrid(constraints: Constraints): Grid {
    if (constraints.widthGroup != null) {
      throw UiDslException("Sub-grids cannot use widthGroup: ${constraints.widthGroup}")
    }

    return (constraints.grid as GridImpl).registerSubGrid(constraints)
  }

  override fun addLayoutComponent(name: String?, comp: Component?) {
    throw UiDslException("Method addLayoutComponent(name: String?, comp: Component?) is not supported")
  }

  override fun removeLayoutComponent(comp: Component?) {
    if (!_rootGrid.unregister(checkComponent(comp))) {
      throw UiDslException("Component has not been registered: $comp")
    }
  }

  override fun preferredLayoutSize(parent: Container?): Dimension {
    requireNotNull(parent)

    synchronized(parent.treeLock) {
      return getPreferredSizeData(parent).preferredSize
    }
  }

  override fun minimumLayoutSize(parent: Container?): Dimension {
    if (!respectMinimumSize) {
      return preferredLayoutSize(parent)
    }

    requireNotNull(parent)

    synchronized(parent.treeLock) {
      return getPreferredSizeData(parent).minimumSize
    }
  }

  override fun maximumLayoutSize(target: Container?): Dimension =
    Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

  override fun layoutContainer(parent: Container?) {
    if (parent == null) {
      throw UiDslException("Parent is null")
    }

    synchronized(parent.treeLock) {
      _rootGrid.layout(parent.width, parent.height, parent.insets, respectMinimumSize)
    }
  }

  override fun getLayoutAlignmentX(target: Container?): Float =
    // Just like other layout managers, no special meaning here
    0.5f

  override fun getLayoutAlignmentY(target: Container?): Float =
    // Just like other layout managers, no special meaning here
    0.5f

  override fun invalidateLayout(target: Container?) {
    // Nothing to do
  }

  fun getConstraints(component: JComponent): Constraints? {
    return _rootGrid.getConstraints(component)
  }

  fun getConstraints(grid: Grid): Constraints? {
    return _rootGrid.getConstraints(grid)
  }

  @ApiStatus.Internal
  internal fun getPreferredSizeData(parent: Container): SizeConstrainsData {
    return _rootGrid.getSizeConstrainsData(parent.insets, respectMinimumSize)
  }
}
