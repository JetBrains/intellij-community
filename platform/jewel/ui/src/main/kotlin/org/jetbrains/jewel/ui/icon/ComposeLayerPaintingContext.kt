// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultBlendMode
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.rendering.BitmapImageResource
import com.intellij.platform.icons.rendering.DrawMode
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.RescalableImageResource
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.fitArea
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

/** A [LayerPaintingContext] implementation that delegates drawing operations to a Compose [DrawScope]. */
@InternalJewelApi
@ApiStatus.Internal
public class ComposeLayerPaintingContext(
    /** The Compose [DrawScope] to which drawing operations are delegated. */
    public val drawScope: DrawScope,
    /**
     * The horizontal offset in pixels of this layer. Used only as the fallback x position when creating a nested layer
     * via [createNestedLayer]; it is not added to the coordinates of drawing operations in this context.
     */
    public override val offsetX: Int = 0,
    /**
     * The layer's vertical offset in pixels. Used only as the fallback y position for a nested layer created via
     * [createNestedLayer]; it is not added to the coordinates of this context's own drawing operations.
     */
    public override val offsetY: Int = 0,
    /** The width of the slot in pixels, or null if unconstrained. */
    override val slotWidth: Int? = null,
    /** The height of the slot in pixels, or null if unconstrained. */
    override val slotHeight: Int? = null,
    private val overrideColorFilter: ColorFilter? = null,
    override val alpha: Float = 1f,
    /** The scaling context used to resolve display density and context scale. */
    override val scaling: ScalingContext = DefaultScalingContext(drawScope.drawContext.density.density, 1f),
) : LayerPaintingContext {
    override fun createNestedLayer(
        x: Int?,
        y: Int?,
        slotWidth: Int?,
        slotHeight: Int?,
        scale: Float,
        alpha: Float,
        overrideColorFilter: ColorFilter?,
    ): LayerPaintingContext {
        return ComposeLayerPaintingContext(
            drawScope,
            x ?: offsetX,
            y ?: offsetY,
            slotWidth,
            slotHeight,
            overrideColorFilter ?: this.overrideColorFilter,
            alpha,
            DefaultScalingContext(scaling.displayDensity, scaling.contextScale * scale),
        )
    }

    override fun drawCircle(color: Color, x: Int, y: Int, radius: Float, alpha: Float, mode: DrawMode) {
        val style =
            if (mode == DrawMode.Stroke) {
                Fill
            } else Fill // TODO Support strokes
        val blendMode =
            if (mode == DrawMode.Clear) {
                BlendMode.Clear
            } else DefaultBlendMode
        drawScope.drawCircle(
            color.toCompose(),
            radius,
            Offset(x.toFloat(), y.toFloat()),
            alpha * this.alpha,
            style = style,
            blendMode = blendMode,
        )
    }

    override fun drawRect(color: Color, x: Int, y: Int, width: Int, height: Int, alpha: Float, mode: DrawMode) {
        val style =
            if (mode == DrawMode.Stroke) {
                Fill
            } else Fill // TODO Support strokes
        val blendMode =
            if (mode == DrawMode.Clear) {
                BlendMode.Clear
            } else DefaultBlendMode
        drawScope.drawRect(
            color.toCompose(),
            Offset(x.toFloat(), y.toFloat()),
            Size(width.toFloat(), height.toFloat()),
            alpha * this.alpha,
            style = style,
            blendMode = blendMode,
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
        colorFilter: ColorFilter?,
    ) {
        when (image) {
            is BitmapImageResource -> {
                drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
            }
            is RescalableImageResource -> {
                drawImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
            }
            is ComposePainterImageResource -> {
                drawComposeImage(image, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
            }
        }
    }

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
        colorFilter: ColorFilter?,
    ) {
        drawComposeImage(
            image.composeBitmap().toView(),
            x,
            y,
            width,
            height,
            srcX,
            srcY,
            srcWidth,
            srcHeight,
            alpha,
            colorFilter,
        )
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
        colorFilter: ColorFilter?,
    ) {
        val realWidth = width ?: image.width
        val realHeight = height ?: image.height
        if (realWidth == null || realHeight == null) return
        val scaledImage = image.composeBitmap(scaling.displayDensity, fitArea(realWidth.px, realHeight.px))
        drawComposeImage(scaledImage, x, y, width, height, srcX, srcY, srcWidth, srcHeight, alpha, colorFilter)
    }

    @Suppress("UnusedParameter")
    private fun drawComposeImage(
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
        colorFilter: ColorFilter?,
    ) {
        if (image.width == 0 || image.height == 0) return
        if (width == 0 || height == 0) return
        if (srcWidth == 0 || srcHeight == 0) return
        val targetSize = IntSize(width ?: image.width, height ?: image.height)
        drawScope.translate(x.toFloat(), y.toFloat()) {
            with(image.painter) {
                draw(
                    Size(targetSize.width.toFloat(), targetSize.height.toFloat()),
                    alpha * this@ComposeLayerPaintingContext.alpha,
                    convertColorFilter(colorFilter ?: image.modifiers?.colorFilter),
                )
            }
        }
    }

    private fun drawComposeImage(
        image: ImageBitmapView,
        x: Int,
        y: Int,
        width: Int?,
        height: Int?,
        srcX: Int,
        srcY: Int,
        srcWidth: Int?,
        srcHeight: Int?,
        alpha: Float,
        colorFilter: ColorFilter? = null,
    ) {
        if (image.imageBitmap.width == 0 || image.imageBitmap.height == 0) return
        if (image.size.width == 0 || image.size.height == 0) return
        val targetSize = IntSize(width ?: image.size.width, height ?: image.size.height)
        if (targetSize.width == 0 || targetSize.height == 0) return
        drawScope.drawImage(
            image.imageBitmap,
            IntOffset(srcX, srcY),
            IntSize(srcWidth ?: image.size.width, srcHeight ?: image.size.height),
            dstOffset = IntOffset(x, y),
            dstSize = targetSize,
            alpha = alpha * this.alpha,
            colorFilter = convertColorFilter(colorFilter),
            filterQuality = FilterQuality.Low,
        )
    }

    private fun convertColorFilter(colorFilter: ColorFilter?): androidx.compose.ui.graphics.ColorFilter? =
        (overrideColorFilter ?: colorFilter)?.toCompose()
}
