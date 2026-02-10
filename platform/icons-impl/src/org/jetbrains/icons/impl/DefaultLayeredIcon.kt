// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.serialization.Serializable
import org.jetbrains.icons.Icon
import org.jetbrains.icons.layers.IconLayer

@Serializable
class DefaultLayeredIcon(
  val layers: List<IconLayer>
): Icon {
  override fun toString(): String {
    return "DefaultIcon(layers=$layers)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultLayeredIcon

    return layers == other.layers
  }

  override fun hashCode(): Int {
    return layers.hashCode()
  }
}

