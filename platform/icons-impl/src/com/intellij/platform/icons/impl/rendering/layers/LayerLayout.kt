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
import com.intellij.platform.icons.impl.design.toFactor
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.impl.rendering.iconScale
import com.intellij.platform.icons.impl.rendering.nested
import com.intellij.platform.icons.impl.rendering.zoom
import com.intellij.platform.icons.rendering.Bounds
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.IconScale
import kotlin.math.roundToInt
import org.jetbrains.annotations.ApiStatus

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
        val dimensions = scale.applyTo(CompoundDimensions(width, height), scaling)
        return Dimensions(
            leftMargin.asPixels(scaling) + dimensions.width + rightMargin.asPixels(scaling),
            topMargin.asPixels(scaling) + dimensions.height + bottomMargin.asPixels(scaling),
        )
    }

    /**
     * The bounds of the layer in [context]. Content with a fit or a fill with `relative = false`, or with a limited fit,
     * is measured in the nested context of the layer, as the layer paints it (see `ScalingContext.nested`). So it has the
     * size that [consumedSpace] measures. Other content is scaled by the factor after the rounding, so a fit fills its
     * area exactly.
     */
    fun placement(context: LayerPaintingContext): Placement {
        val dimensions = Dimensions(width.asPixels(context.scaling), height.asPixels(context.scaling))
        val factor = scale?.toFactor(context.scaling, dimensions.width, dimensions.height) ?: 1f
        val scaled = context.scaling.nested(factor)
        val placement =
            Bounds(
                leftMargin.asPixels(context.scaling),
                topMargin.asPixels(context.scaling),
                width.scaled(dimensions.width, factor, scaled),
                height.scaled(dimensions.height, factor, scaled),
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

/** This size of [pixels] in pixels, scaled by [factor]: in the nested context [scaled] if it depends on the context scale. */
private fun CompoundSize.scaled(pixels: Int, factor: Float, scaled: ScalingContext): Int =
    if (dependsOnContextScale()) asPixels(scaled) else (pixels * factor).roundToInt()

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
            CompoundDimensions(width.compoundSize().relativeTo(relative), height.compoundSize().relativeTo(relative))
        }
        is FillAreaScale -> {
            CompoundDimensions(width.compoundSize().relativeTo(relative), height.compoundSize().relativeTo(relative))
        }
        is FactorScale -> {
            CompoundDimensions(dimensions.width * factor.toFloat(), dimensions.height * factor.toFloat())
        }
        else -> dimensions
    }
}

/**
 * The size of [dimensions] with this scale in [context]. An area with `relative = false` ignores the context scale but
 * keeps the scale of the whole icon, see [DefaultScalingContext.iconScale].
 */
private fun IconScale?.applyTo(dimensions: CompoundDimensions, context: ScalingContext): Dimensions {
    val scaling = if (isRelative()) context else DefaultScalingContext(context.displayDensity, context.iconScale, context.iconScale)
    if (this == null) return dimensions.asPixels(scaling)
    if (this is FitAreaScale && (!upscale || !downscale)) {
        // the area is a limit: the content keeps its own size unless the limit resizes it
        val content = dimensions.asPixels(context)
        val factor = toFactor(context, content.width, content.height)
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

    /**
     * The area of a fit or a fill with `relative = false`. It ignores the context scale but keeps the scale of the whole
     * icon, see [DefaultScalingContext.iconScale].
     */
    private var notRelative: CompoundSize? = null

    internal constructor(deferredFit: DeferredFit) : this(DefaultPixel(0), DefaultDisplayPoint(0.0)) {
        this.deferredFit = deferredFit
    }

    /** A size in logical pixels that already contains the zoom of the context, see [withoutZoom]. */
    private var logical: CompoundSize? = null

    private constructor(notRelative: CompoundSize?, logical: CompoundSize?) : this(DefaultPixel(0), DefaultDisplayPoint(0.0)) {
        this.notRelative = notRelative
        this.logical = logical
    }

    /**
     * This size as a size in logical pixels that already contains the zoom of the context, see
     * [DefaultScalingContext.zoom]. The zoom is taken out of the context scale, and the rest of the context scale
     * applies. For example, a legacy Swing icon of a fixed size keeps that size at each IDE zoom.
     */
    @ApiStatus.Internal
    fun withoutZoom(): CompoundSize = CompoundSize(notRelative = null, logical = this)

    /** Whether this size is resolved in its own context, so it cannot be merged with another size. */
    private val isWrapper: Boolean
        get() = deferredFit != null || notRelative != null || logical != null

    /** This size, or for [relative] = `false` this size as the area of a fit or a fill with `relative = false`. */
    internal fun relativeTo(relative: Boolean): CompoundSize = if (relative) this else CompoundSize(notRelative = this, logical = null)

    /**
     * Whether the size in pixels is not linear in the context scale: a limited fit, or an area with `relative = false`, is
     * in it.
     */
    internal fun dependsOnContextScale(): Boolean =
        deferredFit != null ||
            notRelative != null ||
            nestedCompounds.any { it.dependsOnContextScale() } ||
            pickMaxSet.any { it.dependsOnContextScale() }

    operator fun plus(other: CompoundSize): CompoundSize =
        if (
            other.pickMaxSet.isNotEmpty() ||
                multiplier != 1f ||
                other.multiplier != 1f ||
                isWrapper ||
                other.isWrapper
        ) {
            CompoundSize(0.px, 0.dp, listOf(this, other))
        } else {
            CompoundSize(pixels + other.pixels, dp + other.dp, nestedCompounds + other.nestedCompounds)
        }

    operator fun times(other: Float): CompoundSize =
        if (isWrapper) CompoundSize(0.px, 0.dp, listOf(this), emptyList(), other)
        else CompoundSize(pixels, dp, nestedCompounds, pickMaxSet, multiplier * other)

    operator fun times(other: Double): CompoundSize = times(other.toFloat())

    fun asPixels(scaling: ScalingContext, scale: Float = 1f): Int {
        deferredFit?.let {
            return it.asPixels(scaling, scale)
        }
        notRelative?.let {
            return it.asPixels(DefaultScalingContext(scaling.displayDensity, scaling.iconScale, scaling.iconScale), scale)
        }
        logical?.let {
            val zoom = scaling.zoom.takeIf { zoom -> zoom > 0f } ?: 1f
            return it.asPixels(DefaultScalingContext(scaling.displayDensity, scaling.contextScale / zoom, scaling.iconScale), scale)
        }
        if (multiplier != 1f && dependsOnContextScale()) {
            // a parent layer paints its layers in a nested context, see `ScalingContext.nested`, so they measure in it too
            return CompoundSize(pixels, dp, nestedCompounds, pickMaxSet).asPixels(scaling.nested(multiplier), scale)
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
        val factor = fit.toFactor(scaling, width, height)
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
