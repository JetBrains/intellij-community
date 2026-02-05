// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.MaxIconUnit
import org.jetbrains.icons.design.IconUnit

@Serializable
@ExperimentalIconsApi
class HeightIconModifier(
  val height: IconUnit
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HeightIconModifier

    return height == other.height
  }

  override fun hashCode(): Int {
    return height.hashCode()
  }

  override fun toString(): String {
    return "HeightIconModifier(height=$height)"
  }
}

fun IconModifier.height(height: IconUnit): IconModifier {
  return this then HeightIconModifier(height)
}

fun IconModifier.size(size: IconUnit): IconModifier {
  return this.width(size).height(size)
}

fun IconModifier.fillMaxSize(): IconModifier {
  return this.size(MaxIconUnit)
}