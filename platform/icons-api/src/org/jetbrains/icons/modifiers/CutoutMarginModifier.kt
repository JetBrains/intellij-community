// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.IconUnit

@Serializable
@ExperimentalIconsApi
/**
 * Add cutout margin to the specific layer, which will clear the surrounding area
 * Currently supported only by shape and image layers, image layer will not consider
 * internal svg shape and will cut out rectangular area.
 */
class CutoutMarginModifier(
  val size: IconUnit
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CutoutMarginModifier

    return size == other.size
  }

  override fun hashCode(): Int {
    return size.hashCode()
  }

  override fun toString(): String {
    return "CutoutMarginModifier(size=$size)"
  }
}

fun IconModifier.cutoutMargin(size: IconUnit): IconModifier {
  return this then CutoutMarginModifier(size)
}