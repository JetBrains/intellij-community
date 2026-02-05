// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.layers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.Icon
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.modifiers.IconModifier

@Serializable
class IconIconLayer(
  val icon: Icon,
  override val modifier: IconModifier
): IconLayer {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IconIconLayer

    if (icon != other.icon) return false
    if (modifier != other.modifier) return false

    return true
  }

  override fun hashCode(): Int {
    var result = icon.hashCode()
    result = 31 * result + modifier.hashCode()
    return result
  }

  override fun toString(): String {
    return "IconIconLayer(icon=$icon, modifier=$modifier)"
  }
}