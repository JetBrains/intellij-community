// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.options

import com.intellij.openapi.util.registry.Registry

/**
 * @author Konstantin Bulenkov
 */
object DefaultImageEditorSettings {
  private const val SHOW_CHESSBOARD_KEY = "ide.images.show.chessboard"
  private const val SHOW_GRID_KEY = "ide.images.show.grid"

  var showChessboard: Boolean
    get() = Registry.`is`(SHOW_CHESSBOARD_KEY)
    set(value) = Registry.get(SHOW_CHESSBOARD_KEY).setValue(value)

  var showGrid: Boolean
    get() = Registry.`is`(SHOW_GRID_KEY)
    set(value) = Registry.get(SHOW_GRID_KEY).setValue(value)
}