// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.IconUnit

@Serializable
@ExperimentalIconsApi
class MarginIconModifier(
  val left: IconUnit,
  val top: IconUnit,
  val right: IconUnit,
  val bottom: IconUnit
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MarginIconModifier

    if (left != other.left) return false
    if (top != other.top) return false
    if (right != other.right) return false
    if (bottom != other.bottom) return false

    return true
  }

  override fun hashCode(): Int {
    var result = left.hashCode()
    result = 31 * result + top.hashCode()
    result = 31 * result + right.hashCode()
    result = 31 * result + bottom.hashCode()
    return result
  }

  override fun toString(): String {
    return "MarginIconModifier(left=$left, top=$top, right=$right, bottom=$bottom)"
  }
}

@ExperimentalIconsApi
fun IconModifier.margin(left: IconUnit, top: IconUnit, right: IconUnit, bottom: IconUnit): IconModifier {
  return this then MarginIconModifier(left, top, right, bottom)
}

@ExperimentalIconsApi
fun IconModifier.margin(all: IconUnit): IconModifier {
  return margin(all, all, all, all)
}

@ExperimentalIconsApi
fun IconModifier.margin(vertical: IconUnit, horizontal: IconUnit): IconModifier {
  return margin(horizontal, vertical, horizontal, vertical)
}