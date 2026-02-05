// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.DeferredIcon
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.impl.DefaultDeferredIcon
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DeferredIconEventHandler
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.rendering.createRenderer
import org.jetbrains.annotations.ApiStatus

internal class DefaultDeferredIconRenderer(
    override val icon: DefaultDeferredIcon,
    val renderingContext: RenderingContext,
) : IconRenderer, DeferredIconEventHandler {
    private var isDone = false
    private var renderer = icon.placeholder?.createRenderer(renderingContext)

    override fun whenDone(deferredIcon: DeferredIcon, resolvedIcon: Icon) {
        renderer = resolvedIcon.createRenderer(renderingContext)
        isDone = true
        renderingContext.updateFlow.triggerUpdate()
    }

    @ApiStatus.Internal
    override fun render(paintingContext: LayerPaintingContext) {
        if (!isDone) {
            DefaultIconManager.getDefaultManagerInstance().scheduleEvaluation(icon)
        }
        renderer?.render(paintingContext)
    }

    @ApiStatus.Internal
    override fun calculateUsedDimensions(scaling: ScalingContext): Dimensions =
        renderer?.calculateUsedDimensions(scaling) ?: Dimensions(0, 0)
}
