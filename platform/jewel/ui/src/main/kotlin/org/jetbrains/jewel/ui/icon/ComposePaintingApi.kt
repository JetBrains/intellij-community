// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.filters.ColorFilter
import org.jetbrains.icons.rendering.BitmapImageResource
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.FitAreaScale
import org.jetbrains.icons.rendering.ImageResource
import org.jetbrains.icons.rendering.RescalableImageResource
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import kotlin.math.roundToInt
import org.jetbrains.icons.design.Color
import org.jetbrains.icons.rendering.DrawMode
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@InternalJewelApi
@ApiStatus.Internal
public class ComposePaintingApi(
    public val drawScope: DrawScope,
    private val customBounds: Bounds? = null, 
    private val overrideColorFilter: ColorFilter? = null,
    override val scaling: ScalingContext = ComposeScalingContext(drawScope.drawContext.density.density)
): PaintingApi {
    private var usedBounds: Bounds? = null
    
    override val bounds: Bounds = customBounds ?: Bounds(
        0,
        0,
      drawScope.size.width.roundToInt(),
      drawScope.size.height.roundToInt()
    )
    
    override fun withCustomContext(
        bounds: Bounds, 
        overrideColorFilter: ColorFilter?
    ): PaintingApi {
        return ComposePaintingApi(drawScope, bounds, overrideColorFilter ?: this.overrideColorFilter)
    }

    override fun drawCircle(color: Color, x: Int, y: Int, radius: Float, alpha: Float, mode: DrawMode) {
        val style = if (mode == DrawMode.Stroke) {
            Fill
        } else Fill // TODO Support strokes
        val blendMode = if (mode == DrawMode.Clear) {
            BlendMode.Clear
        } else DefaultBlendMode
        drawScope.drawCircle(
            color.toCompose(),
            radius,
            Offset(x.toFloat(), y.toFloat()), 
            alpha,
            style = style,
            blendMode = blendMode
        )
    }

    override fun drawRect(color: Color, x: Int, y: Int, width: Int, height: Int, alpha: Float, mode: DrawMode) {
        val style = if (mode == DrawMode.Stroke) {
            Fill
        } else Fill // TODO Support strokes
        val blendMode = if (mode == DrawMode.Clear) {
            BlendMode.Clear
        } else DefaultBlendMode
        drawScope.drawRect(
            color.toCompose(),
            Offset(x.toFloat(), y.toFloat()),
            Size(width.toFloat(), height.toFloat()),
            alpha,
            style = style,
            blendMode = blendMode
        )
    }

    override fun drawImage(
        image: ImageResource,
        x: Int,
        y: Int,
        width: Int?,
        height: Int?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int?,
        srcHeight: Int?,
        alpha: Float,
        colorFilter: ColorFilter?
    ) {
        when (image) {
            is BitmapImageResource -> {
                drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
            }
            is RescalableImageResource -> {
                drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
            }
            is ComposePainterImageResource -> {
                drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
            }
        }
    }

    override fun getUsedBounds(): Bounds = usedBounds ?: bounds

    private fun drawImage(
      image: BitmapImageResource,
      x: Int,
      y: Int,
      width: Int?,
      height: Int?,
      srcX: Int,
      srcY: Int,
      srcWidth: Int?,
      srcHeight: Int?,
      alpha: Float,
      colorFilter: ColorFilter?
    ) {
        drawComposeImage(image.composeBitmap(), x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
    }

    private fun drawImage(
        image: RescalableImageResource,
        x: Int,
        y: Int,
        width: Int?,
        height: Int?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int?,
        srcHeight: Int?,
        alpha: Float,
        colorFilter: ColorFilter?
    ) {
        val scaledImage = image.composeBitmap(FitAreaScale(width ?: bounds.width, height ?: bounds.width))
        drawComposeImage(scaledImage, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
    }

    private fun drawImage(
        image: ComposePainterImageResource,
        x: Int,
        y: Int,
        width: Int?,
        height: Int?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int?,
        srcHeight: Int?,
        alpha: Float,
        colorFilter: ColorFilter?
    ) {
        if (image.width == 0 || image.height == 0) return
        val targetSize = IntSize(width ?: bounds.width, height ?: bounds.height)
        drawScope.translate(x.toFloat(), y.toFloat()) {
            with(image.painter) {
                draw(
                    Size(
                        targetSize.width.toFloat(), 
                        targetSize.height.toFloat()
                    ),
                    alpha,
                    convertColorFilter(colorFilter ?: image.modifiers?.colorFilter)
                )
            }
        }
    }
    
    private fun drawComposeImage(
        image: ImageBitmap,
        x: Int,
        y: Int,
        width: Int?,
        height: Int?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int?,
        srcHeight: Int?,
        alpha: Float,
        colorFilter: ColorFilter? = null
    ) {
        if (image.width == 0 || image.height == 0) return
        val targetSize = IntSize(width ?: image.width, height ?: image.height)
        if (targetSize.width == 0 || targetSize.height == 0) return
        drawScope.drawImage(
            image,
            IntOffset(srcX, srcY),
            IntSize(srcWidth ?: image.width, srcHeight ?: image.height),
            dstOffset = IntOffset(x, y),
            dstSize = targetSize,
            alpha = alpha,
            colorFilter = convertColorFilter(colorFilter),
            filterQuality = FilterQuality.Low
        )
    }
    
    private fun convertColorFilter(colorFilter: ColorFilter?): androidx.compose.ui.graphics.ColorFilter? {
        return (overrideColorFilter ?: colorFilter)?.toCompose()
    }
}

@GenerateDataFunctions
internal class ComposeScalingContext(
    override val display: Float
) : ScalingContext {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComposeScalingContext

        return display == other.display
    }

    override fun hashCode(): Int {
        return display.hashCode()
    }

    override fun toString(): String {
        return "ComposeScalingContext(display=$display)"
    }
}
