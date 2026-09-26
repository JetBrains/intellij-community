// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.impl.IconAnimationFrame
import com.intellij.platform.icons.impl.layers.AnimatedIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext
import kotlin.math.cos

class AnimatedIconLayerRenderer(
  private val layer: AnimatedIconLayer,
  private val renderingContext: DefaultRenderingContext,
) : IconLayerRenderer {
  private val currentContext = renderingContext.adjustTo(layer)
  private val frameRenderers = createFrameRenderers()
  private var lastFrame = FrameData(-1, 0, 0, 0)

  override val layout: LayerLayout = applyLayout()

  private fun createFrameRenderers(): List<AnimatedIconFrameRenderer> {
    val renderers = mutableListOf<AnimatedIconFrameRenderer>()
    for (frame in layer.frames) {
      val base = BaseAnimatedIconFrameRenderer(frame, currentContext)
      if (frame.fadeIn > 0L) {
        renderers.add(FadingAnimatedIconFrameRenderer(base, frame.fadeIn, FadingAnimatedIconFrameRenderer.FadeType.FadeIn))
      }
      if (frame.duration > 0L) {
        renderers.add(base)
      }
      if (frame.fadeOut > 0L) {
        renderers.add(FadingAnimatedIconFrameRenderer(base, frame.fadeOut, FadingAnimatedIconFrameRenderer.FadeType.FadeOut))
      }
    }
    return renderers
  }

  private fun applyLayout(): LayerLayout {
    val dimensions = frameRenderers.maxCompoundSize { it.layout.consumedSpace() }
    return applyLayout(layer.modifier, dimensions.width, dimensions.height)
  }

  override fun render(paintingContext: LayerPaintingContext) {
    val placement = layout.placement(paintingContext)
    val nestedLayerApi =
      paintingContext.createNestedLayer(
        placement.bounds.x,
        placement.bounds.y,
        placement.bounds.width,
        placement.bounds.height,
        scale = placement.scale,
      )
    val currentFrameData = calculateAndSetNewFrameData()
    val elapsedTime = System.currentTimeMillis() - currentFrameData.timestamp
    frameRenderers[currentFrameData.frame].render(nestedLayerApi, elapsedTime)
    if (currentFrameData.refreshDuration > 0L) {
      renderingContext.updateFlow.triggerDelayedUpdate(currentFrameData.refreshDuration)
    }
  }

  private fun calculateAndSetNewFrameData(): FrameData {
    val currentLastFrame = lastFrame
    // Handle first frame
    if (currentLastFrame.timestamp == -1L) {
      val remaining = if (frameRenderers.isNotEmpty()) frameRenderers[0].getDuration() else 0L
      val refresh = if (frameRenderers.isNotEmpty()) frameRenderers[0].getRefreshDuration() else 0L
      val newData = FrameData(System.currentTimeMillis(), 0, remaining, refresh)
      lastFrame = newData
      return newData
    }
    // Handle next frame
    val elapsedMillis = System.currentTimeMillis() - lastFrame.timestamp
    if (elapsedMillis > currentLastFrame.remainingDuration) {
      val index = (currentLastFrame.frame + 1) % frameRenderers.size
      val remaining = frameRenderers[index].getDuration()
      val refresh = frameRenderers[index].getRefreshDuration()
      val newData = FrameData(System.currentTimeMillis(), index, remaining, refresh)
      lastFrame = newData
      return newData
    }
    else {
      return lastFrame
    }
  }

  private class FrameData(val timestamp: Long, val frame: Int, val remainingDuration: Long, val refreshDuration: Long)
}

interface AnimatedIconFrameRenderer {
  fun getDuration(): Long
  fun getRefreshDuration(): Long
  val layout: LayerLayout
  fun render(paintingContext: LayerPaintingContext, elapsedMillis: Long)
}

private class FadingAnimatedIconFrameRenderer(val base: BaseAnimatedIconFrameRenderer, private val duration: Long, val fadeType: FadeType) : AnimatedIconFrameRenderer {
  override val layout: LayerLayout = base.layout

  override fun getDuration(): Long {
    return duration
  }

  override fun getRefreshDuration(): Long {
    return 50 // refresh rate for fading animation
  }

  override fun render(paintingContext: LayerPaintingContext, elapsedMillis: Long) {
    val alpha = when (fadeType) {
      FadeType.FadeIn -> fadeInAlpha(elapsedMillis, duration)
      FadeType.FadeOut -> fadeOutAlpha(elapsedMillis, duration)
    }
    val nested = paintingContext.createNestedLayer(alpha = alpha)
    base.render(nested, elapsedMillis)
  }

  private fun fadeInAlpha(elapsedMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0) return 1f

    val progress = (elapsedMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)
    return ((1 - cos(Math.PI * progress)) / 2).toFloat()
  }

  private fun fadeOutAlpha(elapsedMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0) return 0f

    val progress = (elapsedMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)
    return ((cos(Math.PI * progress) + 1) / 2).toFloat()
  }

  enum class FadeType { FadeIn, FadeOut }
}

private class BaseAnimatedIconFrameRenderer(val frame: IconAnimationFrame, renderingContext: RenderingContext) : AnimatedIconFrameRenderer {
  private val renderers = IconLayerManager.createRenderers(frame.layers, renderingContext)

  override fun getDuration(): Long {
    return frame.duration
  }

  override fun getRefreshDuration(): Long {
    return frame.duration
  }

  override val layout: LayerLayout = applyLayout()

  private fun applyLayout(): LayerLayout {
    val dimensions = renderers.maxCompoundSize { it.layout.consumedSpace() }
    return LayerLayout(
      0.dp.compoundSize(),
      0.dp.compoundSize(),
      0.dp.compoundSize(),
      0.dp.compoundSize(),
      dimensions.width,
      dimensions.height,
    )
  }

  override fun render(paintingContext: LayerPaintingContext, elapsedMillis: Long) {
    for (layer in renderers) {
      layer.render(paintingContext)
    }
  }
}