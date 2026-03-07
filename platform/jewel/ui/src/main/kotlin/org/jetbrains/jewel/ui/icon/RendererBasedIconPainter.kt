// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.IconRenderer
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.jewel.foundation.InternalJewelApi

@InternalJewelApi
@ApiStatus.Internal
public class RendererBasedIconPainter(private val iconRenderer: IconRenderer, private val scaling: ScalingContext) : Painter() {
    override val intrinsicSize: Size = iconRenderer.calculateExpectedDimensions(scaling).toComposeSize()

    private var layerPaint: Paint? = null
    private fun obtainPaint(): Paint {
        var target = layerPaint
        if (target == null) {
            target = Paint()
            layerPaint = target
        }
        return target
    }
    
    override fun DrawScope.onDraw() {
        val api = ComposePaintingApi(this, scaling = scaling)
        val layerRect = Rect(Offset.Zero, Size(size.width, size.height))
        drawIntoCanvas { canvas ->
            canvas.withSaveLayer(layerRect, obtainPaint()) {
                iconRenderer.render(api)
            }
        }
    }

    public companion object {
        @Composable
        @InternalJewelApi
        @ApiStatus.Internal
        public fun inferScalingContext(): ScalingContext {
            return ComposeScalingContext(
                LocalDensity.current.density
            )
        }
    }
}

private fun Dimensions.toComposeSize(): Size {
    return Size(width.toFloat(), height.toFloat())
}
