// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.options

import com.intellij.openapi.util.registry.Registry

/**
 * @author Konstantin Bulenkov
 */
object DefaultImageEditorSettings {
  private const val SHOW_CHESSBOARD_KEY = "ide.images.show.chessboard"
  private const val CHESSBOARD_CELL_SIZE_KEY = "ide.images.chessboard.cell.size"
  private const val SHOW_GRID_KEY = "ide.images.show.grid"
  private const val SHOW_GRID_WHEN_KEY = "ide.images.show.grid.only.when.zoom.factor.equal.or.more.than"
  private const val SHOW_GRID_AFTER_X_PIXELS_KEY = "ide.images.show.grid.after.every.x.pixels"

  var showChessboard: Boolean
    get() = Registry.`is`(SHOW_CHESSBOARD_KEY)
    set(value) = Registry.get(SHOW_CHESSBOARD_KEY).setValue(value)

  var chessboardCellSize: Int
    get() = Registry.intValue(CHESSBOARD_CELL_SIZE_KEY, 5)
    set(value) = Registry.get(CHESSBOARD_CELL_SIZE_KEY).setValue(value)

  var showGrid: Boolean
    get() = Registry.`is`(SHOW_GRID_KEY)
    set(value) = Registry.get(SHOW_GRID_KEY).setValue(value)

  var showGridWhenZoomEqualOrMoreThan: Int
    get() = Registry.intValue(SHOW_GRID_WHEN_KEY, 3)
    set(value) = Registry.get(SHOW_GRID_WHEN_KEY).setValue(value)

  var showGridAfterEveryXPixels: Int
    get() = Registry.intValue(SHOW_GRID_AFTER_X_PIXELS_KEY, 1)
    set(value) = Registry.get(SHOW_GRID_AFTER_X_PIXELS_KEY).setValue(value)
}