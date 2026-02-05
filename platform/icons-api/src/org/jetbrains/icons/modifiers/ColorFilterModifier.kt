// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.BlendMode
import org.jetbrains.icons.design.Color
import org.jetbrains.icons.filters.ColorFilter
import org.jetbrains.icons.filters.TintColorFilter

@Serializable
@ExperimentalIconsApi
class ColorFilterModifier(
  val colorFilter: ColorFilter
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ColorFilterModifier

    return colorFilter == other.colorFilter
  }

  override fun hashCode(): Int {
    return colorFilter.hashCode()
  }

  override fun toString(): String {
    return "ColorFilterModifier(colorFilter=$colorFilter)"
  }

}

fun IconModifier.colorFilter(colorFilter: ColorFilter): IconModifier {
  return this then ColorFilterModifier(colorFilter)
}

fun IconModifier.tintColor(color: Color, blendMode: BlendMode): IconModifier {
  return colorFilter(TintColorFilter(color, blendMode))
}
