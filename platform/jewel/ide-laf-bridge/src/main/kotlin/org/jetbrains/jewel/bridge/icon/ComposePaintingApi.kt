// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.icon

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.icons.api.BitmapImageResource
import org.jetbrains.icons.api.Bounds
import org.jetbrains.icons.api.EmptyBitmapImageResource
import org.jetbrains.icons.api.FitAreaScale
import org.jetbrains.icons.api.PaintingApi
import org.jetbrains.icons.api.RescalableImageResource
import kotlin.math.roundToInt

public class ComposePaintingApi(
    public val drawScope: DrawScope
): PaintingApi {
    override val bounds: Bounds = Bounds(
      drawScope.size.width.roundToInt(),
      drawScope.size.height.roundToInt()
    )

    override fun drawImage(
      image: BitmapImageResource,
      x: Int,
      y: Int,
      width: Int?,
      height: Int?,
    ) {
        drawComposeImage(image.composeBitmap(), x, y, width, height)
    }

    override fun drawImage(image: RescalableImageResource, x: Int, y: Int, width: Int?, height: Int?) {
        drawComposeImage(image.composeBitmap(FitAreaScale(bounds.width, bounds.height)), x, y, width, height)
    }
    
    private fun drawComposeImage(
        image: ImageBitmap,
        x: Int,
        y: Int,
        width: Int?,
        height: Int?,
    ) {
        val targetSize = IntSize(width ?: image.width, height ?: image.height)
        if (targetSize.width == 0 || targetSize.height == 0) return
        drawScope.drawImage(
            image,
            IntOffset(x, y),
            targetSize,
            dstSize = targetSize,
            alpha = 1.0f,
            colorFilter = null,
            filterQuality = FilterQuality.Low
        )
    }
}
