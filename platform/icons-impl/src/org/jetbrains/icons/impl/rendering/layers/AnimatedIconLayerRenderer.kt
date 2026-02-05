// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.layers.AnimatedIconLayer
import org.jetbrains.icons.impl.layers.IconLayerManager
import org.jetbrains.icons.impl.rendering.IconAnimationFrame
import org.jetbrains.icons.impl.rendering.modifiers.applyTo

class AnimatedIconLayerRenderer(
  private val layer: AnimatedIconLayer,
  private val renderingContext: RenderingContext
) : IconLayerRenderer {
  private val currentContext = renderingContext.adjustTo(layer)
  private val frameRenderers = layer.frames.map { AnimatedIconFrameRenderer(it, currentContext) }
  private var lastFrame = FrameData(System.currentTimeMillis(), 0, 0)

  override fun render(api: PaintingApi) {
    val layout = DefaultLayerLayout(
      Bounds(
        0,
        0,
        api.bounds.width,
        api.bounds.height,
      ),
      api.bounds
    )
    val nestedLayerApi = api.withCustomContext(layer.modifier.applyTo(layout, api.scaling).calculateFinalBounds())
    val currentFrameData = calculateAndSetNewFrameData()
    frameRenderers[currentFrameData.frame].render(nestedLayerApi)
    if (currentFrameData.remainingDuration > 0L) {
      renderingContext.updateFlow.triggerDelayedUpdate(currentFrameData.remainingDuration)
    }
  }

  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return frameRenderers[lastFrame.frame].calculateExpectedDimensions(scaling)
  }

  private fun calculateAndSetNewFrameData(): FrameData {
    val currentLastFrame = lastFrame
    val elapsedMillis = System.currentTimeMillis() - lastFrame.timestamp
    if (elapsedMillis > currentLastFrame.remainingDuration) {
      val index = (currentLastFrame.frame + 1) % frameRenderers.size
      val remaining = frameRenderers[index].frame.duration
      val newData = FrameData(System.currentTimeMillis(), index, remaining)
      lastFrame = newData
      return newData
    } else return lastFrame
  }

  private class FrameData(
    val timestamp: Long,
    val frame: Int,
    val remainingDuration: Long
  )

  private class AnimatedIconFrameRenderer(
    val frame: IconAnimationFrame,
    renderingContext: RenderingContext
  ) {
    private val renderers = IconLayerManager.createRenderers(frame.layers, renderingContext)

    fun render(api: PaintingApi) {
      for (layer in renderers) {
        layer.render(api)
      }
    }

    fun calculateExpectedDimensions(scalingContext: ScalingContext): Dimensions {
      var width = 0
      var height = 0
      for (layer in renderers) {
        val dimensions = layer.calculateExpectedDimensions(scalingContext)
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
}