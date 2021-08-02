// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.gridLayout

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class HorizontalAlign {
  LEFT,
  CENTER,
  RIGHT,
  FILL
}

@ApiStatus.Experimental
enum class VerticalAlign {
  TOP,
  CENTER,
  BOTTOM,
  FILL
}

@ApiStatus.Experimental
data class JBConstraints(
  /**
   * Grid destination
   */
  val grid: JBGrid,
  /**
   * Cell x coordinate
   */
  val x: Int,
  /**
   * Cell y coordinate
   */
  val y: Int,
  /**
   * Columns number occupied by the cell
   */
  val width: Int = 1,
  /**
   * Rows number occupied by the cell
   */
  val height: Int = 1,
  /**
   * Horizontal alignment of content inside the cell
   */
  val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
  /**
   * Vertical alignment of content inside the cell
   */
  val verticalAlign: VerticalAlign = VerticalAlign.TOP,
  /**
   * Gaps between grid cell bounds and component
   */
  val gaps: Gaps = Gaps.EMPTY,
  /**
   * Visual gaps between component bounds and its content. Can be used when component has focus ring outside of
   * its usual size. In such case components size is increased on focus size (so focus ring is not clipped)
   * and [visualPaddings] should be set to maintain right alignments
   *
   * 1. Layout manager aligns components by their content
   * 2. Calculated cell size with gaps equals to component.bounds + [gaps] - [visualPaddings]
   */
  val visualPaddings: Gaps = Gaps.EMPTY
) {

  init {
    checkNonNegative(::x)
    checkNonNegative(::y)
    checkPositive(::width)
    checkPositive(::height)
  }
}
