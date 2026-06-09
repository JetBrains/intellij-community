// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.impl.IconAnimationFrame
import com.intellij.platform.icons.impl.layers.AnimatedIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext

class AnimatedIconLayerRenderer(
    private val layer: AnimatedIconLayer,
    private val renderingContext: DefaultRenderingContext,
) : IconLayerRenderer {
    private val currentContext = renderingContext.adjustTo(layer)
    private val frameRenderers = layer.frames.map { AnimatedIconFrameRenderer(it, currentContext) }
    private var lastFrame = FrameData(-1, 0, 0)

    override val layout: LayerLayout = applyLayout()

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
        frameRenderers[currentFrameData.frame].render(nestedLayerApi)
        if (currentFrameData.remainingDuration > 0L) {
            renderingContext.updateFlow.triggerDelayedUpdate(currentFrameData.remainingDuration)
        }
    }

    private fun calculateAndSetNewFrameData(): FrameData {
        val currentLastFrame = lastFrame
        if (currentLastFrame.timestamp == -1L) {
            val remaining = if (frameRenderers.size > 0) frameRenderers[0].frame.duration else 0L
            val newData = FrameData(System.currentTimeMillis(), 0, remaining)
            lastFrame = newData
            return newData
        }
        val elapsedMillis = System.currentTimeMillis() - lastFrame.timestamp
        if (elapsedMillis > currentLastFrame.remainingDuration) {
            val index = (currentLastFrame.frame + 1) % frameRenderers.size
            val remaining = frameRenderers[index].frame.duration
            val newData = FrameData(System.currentTimeMillis(), index, remaining)
            lastFrame = newData
            return newData
        } else {
            return lastFrame
        }
    }

    private class FrameData(val timestamp: Long, val frame: Int, val remainingDuration: Long)

    private class AnimatedIconFrameRenderer(val frame: IconAnimationFrame, renderingContext: RenderingContext) {
        private val renderers = IconLayerManager.createRenderers(frame.layers, renderingContext)

        val layout: LayerLayout = applyLayout()

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

        fun render(paintingContext: LayerPaintingContext) {
            for (layer in renderers) {
                layer.render(paintingContext)
            }
        }
    }
}
