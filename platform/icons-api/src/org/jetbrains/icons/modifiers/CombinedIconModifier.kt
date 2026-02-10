// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

@Serializable
@ExperimentalIconsApi
class CombinedIconModifier(
  val root: IconModifier,
  val other: IconModifier,
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CombinedIconModifier

    if (root != other.root) return false
    if (other != other.other) return false

    return true
  }

  override fun hashCode(): Int {
    var result = root.hashCode()
    result = 31 * result + other.hashCode()
    return result
  }

  override fun toString(): String {
    return "CombinedIconModifier(root=$root, other=$other)"
  }

}