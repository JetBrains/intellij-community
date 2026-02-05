// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

@Serializable
@ExperimentalIconsApi
class AlphaIconModifier(
  val alpha: Float
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AlphaIconModifier

    return alpha == other.alpha
  }

  override fun hashCode(): Int {
    return alpha.hashCode()
  }

  override fun toString(): String {
    return "AlphaIconModifier(alpha=$alpha)"
  }
}

fun IconModifier.alpha(alpha: Float): IconModifier {
  return this then AlphaIconModifier(alpha)
}