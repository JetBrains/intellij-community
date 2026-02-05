// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.Color

@Serializable
@ExperimentalIconsApi
class StrokeModifier(
  val color: Color
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as StrokeModifier

    return color == other.color
  }

  override fun hashCode(): Int {
    return color.hashCode()
  }

  override fun toString(): String {
    return "StrokeIconModifier(color=$color)"
  }

}

@ExperimentalIconsApi
fun IconModifier.stroke(color: Color): IconModifier {
  return this then StrokeModifier(color)
}