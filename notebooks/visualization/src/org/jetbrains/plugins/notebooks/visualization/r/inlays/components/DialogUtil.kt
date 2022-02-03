/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import java.awt.Dimension
import java.awt.Toolkit

object DialogUtil {
  enum class SizePreference {
    VERY_NARROW,
    NARROW,
    MODERATE,
    WIDE,
    VERY_WIDE,
  }

  fun calculatePreferredSize(sizePreference: SizePreference): Dimension {
    return calculatePreferredSize(sizePreference, sizePreference)
  }

  fun calculatePreferredSize(widthPreference: SizePreference, heightPreference: SizePreference): Dimension {
    val widthRatio = calculateRatioByPreference(widthPreference)
    val heightRatio = calculateRatioByPreference(heightPreference)
    return calculatePreferredSize(widthRatio, heightRatio)
  }

  private fun calculatePreferredSize(widthRatio: Double, heightRatio: Double): Dimension {
    checkRatio(widthRatio, "Width")
    checkRatio(heightRatio, "Height")
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val width = (screenSize.width * widthRatio).toInt()
    val height = (screenSize.height * heightRatio).toInt()
    return Dimension(width, height)
  }

  private fun checkRatio(ratio: Double, name: String) {
    if (ratio <= 0.0 || ratio > 1.0) {
      throw IllegalArgumentException("$name ratio must fall within the range (0.0; 1.0]. Actual value was $ratio")
    }
  }

  private fun calculateRatioByPreference(sizePreference: SizePreference): Double {
    return when (sizePreference) {
      SizePreference.VERY_NARROW -> 0.15
      SizePreference.NARROW -> 0.25
      SizePreference.MODERATE -> 0.5
      SizePreference.WIDE -> 0.75
      SizePreference.VERY_WIDE -> 0.85
    }
  }
}
