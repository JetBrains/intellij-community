// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.icon

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.icons.api.BitmapImageResource
import org.jetbrains.icons.api.Bounds
import org.jetbrains.icons.api.PaintingApi
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
        val targetSize = IntSize(image.width, image.height)
        drawScope.drawImage(
            image.composeBitmap(),
            IntOffset(x, y),
            targetSize,
            dstSize = targetSize,
            alpha = 1.0f,
            colorFilter = null,
            filterQuality = FilterQuality.Low
        )
    }
}
