// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.Icon
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.IconRenderer
import org.jetbrains.icons.rendering.LoadingStrategy
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.DefaultLayeredIcon
import org.jetbrains.icons.impl.rendering.layers.IconLayerRenderer
import org.jetbrains.icons.impl.layers.IconLayerManager

class DefaultIconRenderer(
  val iconInstance: DefaultLayeredIcon,
  private val context: RenderingContext,
  private val loadingStrategy: LoadingStrategy
) : IconRenderer {
  override val icon: Icon = iconInstance
  private var isLoaded = false
  private val layerRenderers = createRenderers()

  private fun createRenderers(): List<IconLayerRenderer> {
    val manager = IconLayerManager.getInstance()
    val renderers = iconInstance.layers.map { manager.createRenderer(it, context) }
    isLoaded = true
    return renderers
  }

  override fun render(api: PaintingApi) {
    for (layer in layerRenderers) {
      layer.render(api)
    }
  }

  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    var width = 0
    var height = 0
    for (layer in layerRenderers) {
      val dimensions = layer.calculateExpectedDimensions(scaling)
      if (dimensions.width > width) {
        width = dimensions.width
      }
      if (dimensions.height > height) {
        height = dimensions.height
      }
    }
    return Dimensions(width, height)
  }
}