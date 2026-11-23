// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering.layers

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.DisplayPoint
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconHorizontalAlign
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.IconVerticalAlign
import com.intellij.platform.icons.design.Pixel
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.design.DefaultDisplayPoint
import com.intellij.platform.icons.impl.design.DefaultPixel
import com.intellij.platform.icons.impl.modifiers.asPixels
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.rendering.Bounds
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.IconScale
import kotlin.math.roundToInt

open class LayerLayout(
    val leftMargin: CompoundSize,
    val topMargin: CompoundSize,
    val rightMargin: CompoundSize,
    val bottomMargin: CompoundSize,
    val width: CompoundSize,
    val height: CompoundSize,
    val colorFilter: ColorFilter? = null,
    val alpha: Float = 1f,
    val cutoutMargin: IconUnit? = null,
    val stroke: Color? = null,
    val align: IconAlign? = null,
    val scale: IconScale? = null,
) {
    fun copy(
        leftMargin: CompoundSize = this.leftMargin,
        topMargin: CompoundSize = this.topMargin,
        rightMargin: CompoundSize = this.rightMargin,
        bottomMargin: CompoundSize = this.bottomMargin,
        width: CompoundSize = this.width,
        height: CompoundSize = this.height,
        colorFilter: ColorFilter? = this.colorFilter,
        alpha: Float = this.alpha,
        cutoutMargin: IconUnit? = this.cutoutMargin,
        stroke: Color? = this.stroke,
        align: IconAlign? = this.align,
        scale: IconScale? = this.scale,
    ): LayerLayout =
        LayerLayout(
            leftMargin,
            topMargin,
            rightMargin,
            bottomMargin,
            width,
            height,
            colorFilter,
            alpha,
            cutoutMargin,
            stroke,
            align,
            scale,
        )

    fun consumedSpace(): CompoundDimensions {
        val dimensions = scale.applyTo(CompoundDimensions(width, height))
        return CompoundDimensions(
            leftMargin + dimensions.width + rightMargin,
            topMargin + dimensions.height + bottomMargin,
        )
    }

    fun consumedSpace(scaling: ScalingContext): Dimensions {
        val dimensions = scale.applyTo(CompoundDimensions(width, height), scaling.displayDensity, scaling.contextScale)
        return Dimensions(
            leftMargin.asPixels(scaling) + dimensions.width + rightMargin.asPixels(scaling),
            topMargin.asPixels(scaling) + dimensions.height + bottomMargin.asPixels(scaling),
        )
    }

    fun placement(context: LayerPaintingContext): Placement {
        val dimensions = Dimensions(width.asPixels(context.scaling), height.asPixels(context.scaling))
        val factor =
            scale?.toFactor(
                context.scaling.displayDensity,
                dimensions.width,
                dimensions.height,
                context.scaling.contextScale,
            ) ?: 1f
        val placement =
            Bounds(
                leftMargin.asPixels(context.scaling),
                topMargin.asPixels(context.scaling),
                (dimensions.width * factor).roundToInt(),
                (dimensions.height * factor).roundToInt(),
            )
        var verticalOffset = 0
        var horizontalOffset = 0
        if (align != null) {
            val bottom = bottomMargin.asPixels(context.scaling)
            val right = rightMargin.asPixels(context.scaling)
            val slotHeight = context.slotHeight
            val slotWidth = context.slotWidth
            if (slotHeight != null && slotHeight > 0) {
                if (align.verticalAlign == IconVerticalAlign.Center) {
                    verticalOffset = slotHeight / 2 - placement.height / 2 - placement.y
                } else if (align.verticalAlign == IconVerticalAlign.Bottom) {
                    verticalOffset = slotHeight - placement.y - bottom - placement.height
                }
            }
            if (slotWidth != null && slotWidth > 0) {
                if (align.horizontalAlign == IconHorizontalAlign.Center) {
                    horizontalOffset = slotWidth / 2 - placement.width / 2 - placement.x
                } else if (align.horizontalAlign == IconHorizontalAlign.Right) {
                    horizontalOffset = slotWidth - placement.x - right - placement.width
                }
            }
        }
        return Placement(
            placement.copyBounds(
                x = (placement.x + context.offsetX + horizontalOffset).coerceAtLeast(0),
                y = (placement.y + context.offsetY + verticalOffset).coerceAtLeast(0),
            ),
            factor,
        )
    }
}

class Placement(val bounds: Bounds, val scale: Float = 1f)

private fun IconScale?.applyTo(dimensions: CompoundDimensions): CompoundDimensions {
    return when (this) {
        // a limited fit only resizes content beyond the limit, which needs pixels to tell: fit when the scale is known
        is FitAreaScale if !upscale || !downscale -> {
            CompoundDimensions(
                CompoundSize(DeferredFit(dimensions.width, dimensions.height, this, isWidth = true)),
                CompoundSize(DeferredFit(dimensions.width, dimensions.height, this, isWidth = false)),
            )
        }
        is FitAreaScale -> {
            CompoundDimensions(width.compoundSize(), height.compoundSize())
        }
        is FillAreaScale -> {
            CompoundDimensions(width.compoundSize(), height.compoundSize())
        }
        is FactorScale -> {
            CompoundDimensions(dimensions.width * factor.toFloat(), dimensions.height * factor.toFloat())
        }
        else -> dimensions
    }
}

private fun IconScale?.applyTo(dimensions: CompoundDimensions, density: Float, contextScale: Float = 1f): Dimensions {
    val scaling = DefaultScalingContext(density, if (isRelative()) contextScale else 1f)
    if (this == null) return dimensions.asPixels(scaling)
    if (this is FitAreaScale && (!upscale || !downscale)) {
        // the area is a limit: the content keeps its own size unless the limit resizes it
        val content = dimensions.asPixels(DefaultScalingContext(density, contextScale))
        val factor = toFactor(density, content.width, content.height, contextScale)
        return Dimensions((content.width * factor).roundToInt(), (content.height * factor).roundToInt())
    }
    return applyTo(dimensions).asPixels(scaling)
}

private fun IconScale?.isRelative(): Boolean =
    when (this) {
        is FitAreaScale -> relative
        is FillAreaScale -> relative
        is FactorScale -> true
        else -> true
    }

class CompoundDimensions(val width: CompoundSize, val height: CompoundSize) {
    fun asPixels(scaling: ScalingContext): Dimensions =
        Dimensions(width.asPixels(scaling).coerceAtLeast(0), height.asPixels(scaling).coerceAtLeast(0))
}

/**
 * CompoundSize is used to calculate generic layout, without knowing density.
 *
 * @param pixels - pixels size
 * @param dp - density-independent pixels size
 * @param nestedCompounds - list of nested CompoundSize instances, when density is known, the sum will be added to the
 *   result
 * @param pickMaxSet - list of CompoundSize instances, when density is known, the biggest will be picked and added to
 *   the result
 */
class CompoundSize(
    val pixels: Pixel,
    val dp: DisplayPoint,
    val nestedCompounds: List<CompoundSize> = emptyList(),
    val pickMaxSet: List<CompoundSize> = emptyList(),
    val multiplier: Float = 1f,
) {
    /** A size that a limited fit computes when the scale is known, see [DeferredFit]. */
    private var deferredFit: DeferredFit? = null

    internal constructor(deferredFit: DeferredFit) : this(DefaultPixel(0), DefaultDisplayPoint(0.0)) {
        this.deferredFit = deferredFit
    }

    operator fun plus(other: CompoundSize): CompoundSize =
        if (
            other.pickMaxSet.isNotEmpty() ||
                multiplier != 1f ||
                other.multiplier != 1f ||
                deferredFit != null ||
                other.deferredFit != null
        ) {
            CompoundSize(0.px, 0.dp, listOf(this, other))
        } else {
            CompoundSize(pixels + other.pixels, dp + other.dp, nestedCompounds + other.nestedCompounds)
        }

    operator fun times(other: Float): CompoundSize =
        if (deferredFit != null) CompoundSize(0.px, 0.dp, listOf(this), emptyList(), other)
        else CompoundSize(pixels, dp, nestedCompounds, pickMaxSet, multiplier * other)

    operator fun times(other: Double): CompoundSize = times(other.toFloat())

    fun asPixels(scaling: ScalingContext, scale: Float = 1f): Int {
        deferredFit?.let {
            return it.asPixels(scaling, scale)
        }
        val nested = nestedCompounds.sumOf { it.asPixels(scaling, scale) }
        val maxed = pickMaxSet.maxOfOrNull { it.asPixels(scaling, scale) } ?: 0
        return ((pixels.asPixels(scaling, scale) + dp.asPixels(scaling, scale) + nested + maxed) * multiplier)
            .roundToInt()
    }
}

/**
 * One side of the content of [contentWidth] x [contentHeight] fitted by the limited [fit]. The factor of [fit] needs both
 * sides in pixels, so it is computed only when the scale is known.
 */
internal class DeferredFit(
    private val contentWidth: CompoundSize,
    private val contentHeight: CompoundSize,
    private val fit: FitAreaScale,
    private val isWidth: Boolean,
) {
    fun asPixels(scaling: ScalingContext, scale: Float): Int {
        val width = contentWidth.asPixels(scaling)
        val height = contentHeight.asPixels(scaling)
        val factor = fit.toFactor(scaling.displayDensity, width, height, scaling.contextScale)
        return ((if (isWidth) width else height) * factor * scale).roundToInt()
    }
}

fun IconUnit.compoundSize(): CompoundSize =
    when (this) {
        is DisplayPoint -> CompoundSize(DefaultPixel(0), this)
        is Pixel -> CompoundSize(this, DefaultDisplayPoint(0.0))
    }

fun List<CompoundSize>.maxCompoundSize(): CompoundSize =
    CompoundSize(DefaultPixel(0), DefaultDisplayPoint(0.0), emptyList(), this)

fun <TItem> List<TItem>.maxCompoundSize(compound: (TItem) -> CompoundDimensions): CompoundDimensions {
    val widths = mutableListOf<CompoundSize>()
    val heights = mutableListOf<CompoundSize>()
    for (item in this) {
        val dimensions = compound(item)
        widths.add(dimensions.width)
        heights.add(dimensions.height)
    }
    return CompoundDimensions(widths.maxCompoundSize(), heights.maxCompoundSize())
}

fun <TItem> List<TItem>.compoundWidthSumHeightMax(compound: (TItem) -> CompoundDimensions): CompoundDimensions {
    var width = CompoundSize(DefaultPixel(0), DefaultDisplayPoint(0.0))
    val heights = mutableListOf<CompoundSize>()
    for (item in this) {
        val dimensions = compound(item)
        width += dimensions.width
        heights.add(dimensions.height)
    }
    return CompoundDimensions(width, heights.maxCompoundSize())
}

fun <TItem> List<TItem>.compoundHeightSumWidthMax(compound: (TItem) -> CompoundDimensions): CompoundDimensions {
    val widths = mutableListOf<CompoundSize>()
    var height = CompoundSize(DefaultPixel(0), DefaultDisplayPoint(0.0))
    for (item in this) {
        val dimensions = compound(item)
        widths.add(dimensions.width)
        height += dimensions.height
    }
    return CompoundDimensions(widths.maxCompoundSize(), height)
}
