// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.modifiers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.design.IconAlign

@Serializable
@ExperimentalIconsApi
class AlignIconModifier(
  val align: IconAlign
): IconModifier {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AlignIconModifier

    return align == other.align
  }

  override fun hashCode(): Int {
    return align.hashCode()
  }

  override fun toString(): String {
    return "AlignIconModifier(align=$align)"
  }
}

fun IconModifier.align(align: IconAlign): IconModifier {
  return this then AlignIconModifier(align)
}
