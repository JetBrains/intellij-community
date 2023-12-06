// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Shape
import javax.swing.UIManager

@ApiStatus.Internal
@ApiStatus.Experimental
sealed class BadgeShapeProvider(
  /**
   * size of a hole around the badge relative to the icon size [0..1]
   */
  val border: Double
) {
  companion object {
    /** @return value from UIManager, or the default value if the given key is not specified. */
    fun getDouble(key: String, default: Double) = when (val value = UIManager.get(key)) {
      is String -> value.toDoubleOrNull() ?: default
      is Number -> value.toDouble()
      else -> default
    }
  }

  /** @return badge (or hole) shape for the icon of the given size */
  abstract fun createShape(width: Int, height: Int, hole: Boolean): Shape?
}
