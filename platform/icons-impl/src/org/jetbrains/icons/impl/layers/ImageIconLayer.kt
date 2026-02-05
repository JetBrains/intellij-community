// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.layers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.rendering.ImageResourceLoader

@Serializable
class ImageIconLayer(
  val loader: ImageResourceLoader,
  override val modifier: IconModifier
) : IconLayer {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ImageIconLayer

    if (loader != other.loader) return false
    if (modifier != other.modifier) return false

    return true
  }

  override fun hashCode(): Int {
    var result = loader.hashCode()
    result = 31 * result + modifier.hashCode()
    return result
  }

  override fun toString(): String {
    return "ImageIconLayer(loader=$loader, modifier=$modifier)"
  }
}