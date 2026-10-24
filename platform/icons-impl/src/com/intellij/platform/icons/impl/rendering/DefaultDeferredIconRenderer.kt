// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.DeferredIconDescriptor
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.DefaultDeferredIconDescriptor
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DeferredIconEventHandler
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.rendering.createRenderer
import org.jetbrains.annotations.ApiStatus

internal class DefaultDeferredIconRenderer(
  override val iconDescriptor: DefaultDeferredIconDescriptor,
  val renderingContext: RenderingContext,
) : IconRenderer, DeferredIconEventHandler {
    private var isDone = false
    private var renderer = iconDescriptor.placeholder?.createRenderer(renderingContext)

    override fun whenDone(deferredIcon: DeferredIconDescriptor, resolvedIconDescriptor: IconDescriptor) {
        renderer = resolvedIconDescriptor.createRenderer(renderingContext)
        isDone = true
        renderingContext.updateFlow.triggerUpdate()
    }

    @ApiStatus.Internal
    override fun render(paintingContext: LayerPaintingContext) {
        if (!isDone) {
            DefaultIconManager.getDefaultManagerInstance().scheduleEvaluation(iconDescriptor)
        }
        val delegate = renderer
        if (IconRenderDebugger.isActive) {
            // the delegate (placeholder / resolved icon renderer) is traced as this renderer's child
            IconRenderDebugger.traceRender(this, paintingContext, calculateUsedDimensions(paintingContext.scaling)) {
                delegate?.render(paintingContext)
            }
        }
        else {
            delegate?.render(paintingContext)
        }
    }

    @ApiStatus.Internal
    override fun calculateUsedDimensions(scaling: ScalingContext): Dimensions =
        renderer?.calculateUsedDimensions(scaling) ?: Dimensions(0, 0)
}
