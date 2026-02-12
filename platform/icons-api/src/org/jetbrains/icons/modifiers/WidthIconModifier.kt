// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.IconUnit

@Serializable
@ExperimentalIconsApi
class WidthIconModifier(
  val width: IconUnit
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WidthIconModifier

    return width == other.width
  }

  override fun hashCode(): Int {
    return width.hashCode()
  }

  override fun toString(): String {
    return "WidthIconModifier(width=$width)"
  }
}

@ExperimentalIconsApi
fun IconModifier.width(width: IconUnit): IconModifier {
  return this then WidthIconModifier(width)
}