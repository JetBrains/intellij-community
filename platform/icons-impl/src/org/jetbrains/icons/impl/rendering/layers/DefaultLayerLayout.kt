// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.design.Color
import org.jetbrains.icons.filters.ColorFilter
import org.jetbrains.icons.rendering.Bounds

interface LayerLayout {
  val layerBounds: Bounds
  val parentBounds: Bounds
  val colorFilter: ColorFilter?
  val alpha: Float
  val cutoutMargin: Float?
  val stroke: Color?

  fun copy(
    layerBounds: Bounds = this.layerBounds,
    parentBounds: Bounds = this.parentBounds,
    colorFilter: ColorFilter? = this.colorFilter,
    alpha: Float = this.alpha,
    cutoutMargin: Float? = this.cutoutMargin,
    stroke: Color? = this.stroke
  ): LayerLayout

  fun calculateFinalBounds(): Bounds
}

open class DefaultLayerLayout(
  override val layerBounds: Bounds,
  override val parentBounds: Bounds,
  override val colorFilter: ColorFilter? = null,
  override val alpha: Float = 1f,
  override val cutoutMargin: Float? = null,
  override val stroke: Color? = null
): LayerLayout {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultLayerLayout

    if (alpha != other.alpha) return false
    if (layerBounds != other.layerBounds) return false
    if (parentBounds != other.parentBounds) return false
    if (colorFilter != other.colorFilter) return false
    if (cutoutMargin != other.cutoutMargin) return false
    if (stroke != other.stroke) return false

    return true
  }

  override fun hashCode(): Int {
    var result = alpha.hashCode()
    result = 31 * result + layerBounds.hashCode()
    result = 31 * result + parentBounds.hashCode()
    result = 31 * result + (colorFilter?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "LayerLayout(layerBounds=$layerBounds, parentBounds=$parentBounds, colorFilter=$colorFilter, alpha=$alpha, stroke=$stroke)"
  }

  override fun calculateFinalBounds(): Bounds {
    return Bounds(
      layerBounds.x + parentBounds.x,
      layerBounds.y + parentBounds.y,
      layerBounds.width,
      layerBounds.height
    )
  }

  override fun copy(
    layerBounds: Bounds,
    parentBounds: Bounds,
    colorFilter: ColorFilter?,
    alpha: Float,
    cutoutMargin: Float?,
    stroke: Color?
  ): DefaultLayerLayout {
    return DefaultLayerLayout(layerBounds, parentBounds, colorFilter, alpha, cutoutMargin, stroke)
  }
}