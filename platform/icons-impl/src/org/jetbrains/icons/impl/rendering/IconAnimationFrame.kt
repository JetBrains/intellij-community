// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import kotlinx.serialization.Serializable
import org.jetbrains.icons.layers.IconLayer

@Serializable
class IconAnimationFrame(
  val layers: List<IconLayer>,
  val duration: Long
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IconAnimationFrame

    if (duration != other.duration) return false
    if (layers != other.layers) return false

    return true
  }

  override fun hashCode(): Int {
    var result = duration.hashCode()
    result = 31 * result + layers.hashCode()
    return result
  }

  override fun toString(): String {
    return "IconAnimationFrame(duration=$duration, layers=$layers)"
  }
}