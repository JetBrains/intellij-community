// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ScalingContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.annotations.ApiStatus

/**
 * The logical size that an image is painted at: the device pixels divided by the screen density, before the rounding to
 * device pixels. It contains the IDE zoom.
 */
@ApiStatus.Internal
class PaintedSize(val width: Double, val height: Double) {
    override fun toString(): String = "PaintedSize(width=$width, height=$height)"
}

/**
 * `@WxH` size variants (`icon@20x20.svg` next to `icon.svg`), chosen per [SizeVariantPolicy]. The Swing and the Compose
 * renderers share these rules. Each function takes the IDE zoom from its caller.
 *
 * - [SizeVariantPolicy.Exact] (default), at parity with the legacy `loadIconCustomVersion`
 *   (platform/core-ui/src/ui/icons/customIconUtil.kt): the variant W×H is the size that a legacy caller asks for. Width and
 *   height are matched independently, see [exactDesign].
 * - [SizeVariantPolicy.Fitting]: the largest existing variant with the aspect ratio of the base artwork that is not larger
 *   than the design size (the painted size divided by the IDE zoom) and is larger than the base artwork. At most
 *   [MAX_FITTING_PROBES] sizes are probed, downward from the design size.
 * - [SizeVariantPolicy.None]: no variants.
 */
@ApiStatus.Internal
object SizeVariants {
    private const val TOLERANCE = 0.01

    /** The largest number of probes for one lookup of [SizeVariantPolicy.Fitting]. */
    const val MAX_FITTING_PROBES: Int = 64

    /**
     * The design size W×H of the variant to draw, or `null` to draw the base artwork. [exists] probes whether the `@WxH`
     * variant exists. [baseDesignWidth] × [baseDesignHeight] is the design size of the base artwork.
     */
    fun chooseVariant(
        policy: SizeVariantPolicy,
        painted: PaintedSize,
        zoom: Double,
        baseDesignWidth: Int,
        baseDesignHeight: Int,
        exists: (width: Int, height: Int) -> Boolean,
    ): Pair<Int, Int>? {
        if (baseDesignWidth <= 0 || baseDesignHeight <= 0 || zoom <= 0.0) return null
        return when (policy) {
            SizeVariantPolicy.None -> null
            SizeVariantPolicy.Exact -> {
                val width = exactDesign(painted.width, zoom, baseDesignWidth) ?: return null
                val height = exactDesign(painted.height, zoom, baseDesignHeight) ?: return null
                (width to height).takeIf {
                    (width != baseDesignWidth || height != baseDesignHeight) && exists(width, height)
                }
            }
            SizeVariantPolicy.Fitting -> fitting(painted, zoom, baseDesignWidth, baseDesignHeight, exists)
        }
    }

    /**
     * The exact logical size of an image of the design size [imageWidth] × [imageHeight] in the device pixels [width] ×
     * [height], if the image is painted at its own size times the context scale of [scaling]. That size is not rounded to
     * device pixels, so that the variant is the one that the scale asks for. Else, for an image with a scale of its own,
     * the size comes from the device pixels.
     */
    fun paintedSize(imageWidth: Int, imageHeight: Int, width: Int, height: Int, scaling: ScalingContext): PaintedSize? {
        val density = scaling.displayDensity.toDouble()
        if (density <= 0.0) return null
        val exactWidth = imageWidth * scaling.contextScale.toDouble()
        val exactHeight = imageHeight * scaling.contextScale.toDouble()
        if ((exactWidth * density).roundToInt() == width && (exactHeight * density).roundToInt() == height) {
            return PaintedSize(exactWidth, exactHeight)
        }
        return PaintedSize(width / density, height / density)
    }

    /** `<path minus .svg>@WxH.svg` if [path] is an SVG, else `null`. */
    fun variantPath(path: String, width: Int, height: Int): String? {
        if (!path.endsWith(".svg")) return null
        return "${path.removeSuffix(".svg")}@${width}x$height.svg"
    }

    /** The key of the variant W×H in a cache of variants. */
    fun key(width: Int, height: Int): Long = (width.toLong() shl 32) or (height.toLong() and 0xFFFFFFFFL)

    /**
     * The design size W that a legacy caller asks for when it paints a side of the design size [base] at the logical size
     * [logical], or `null`. Two legacy callers apply:
     * - `loadIconCustomVersionOrScale(icon, W)` scales by `zoom * W / icon.iconWidth`, and `icon.iconWidth` is the rounded
     *   `JBUIScale.scale(base)`. The painted size then is `base * zoom * zoom * W / JBUIScale.scale(base)`.
     * - A direct scale paints at `W * zoom`, for example `factor(1.25f)` of a 16×16 icon paints at `20 * zoom`.
     * - A scale to `JBUIScale.scale(W)` paints at that integer.
     */
    private fun exactDesign(logical: Double, zoom: Double, base: Int): Int? {
        if (logical <= 0.0) return null
        val baseLogical = (base * zoom).roundToInt()
        integer(logical / zoom * baseLogical / (base * zoom))?.takeIf { it > 0 }?.let { return it }
        integer(logical / zoom)?.takeIf { it > 0 }?.let { return it }
        val rounded = integer(logical) ?: return null
        return designFor(rounded, zoom)
    }

    /**
     * Probes N from the design width down to the base width + 1. The variant is `@NxN` for a square base, and else `@NxM`,
     * with M the height for the aspect ratio of the base. A variant higher than the design height is not probed.
     */
    private fun fitting(
        painted: PaintedSize,
        zoom: Double,
        baseWidth: Int,
        baseHeight: Int,
        exists: (Int, Int) -> Boolean,
    ): Pair<Int, Int>? {
        val designWidth = floor(painted.width / zoom + TOLERANCE).toInt()
        val designHeight = floor(painted.height / zoom + TOLERANCE).toInt()
        var probes = 0
        for (width in designWidth downTo baseWidth + 1) {
            val height = if (baseWidth == baseHeight) width else (width.toDouble() * baseHeight / baseWidth).roundToInt()
            if (height <= baseHeight || height > designHeight) continue
            if (probes++ >= MAX_FITTING_PROBES) return null
            if (exists(width, height)) return width to height
        }
        return null
    }

    private fun integer(value: Double): Int? {
        val rounded = value.roundToInt()
        return if (abs(value - rounded) <= TOLERANCE) rounded else null
    }

    /** The integer design size whose `JBUIScale.scale` at [zoom] is [logical], if any. */
    private fun designFor(logical: Int, zoom: Double): Int? {
        val exact = logical / zoom
        return listOf(floor(exact).toInt(), ceil(exact).toInt())
            .distinct()
            .filter { it > 0 && (it * zoom).roundToInt() == logical }
            .minByOrNull { abs(it - exact) }
    }
}

/**
 * The `@WxH` variants of one image, by W×H. A lookup runs on each paint, so a variant that exists and a variant that does
 * not exist are both kept. The cache keeps the [limit] variants that were used last. The default limit is larger than
 * [SizeVariants.MAX_FITTING_PROBES], so one lookup of [SizeVariantPolicy.Fitting] does not remove its own probes.
 */
@ApiStatus.Internal
class SizeVariantCache<V : Any>(private val limit: Int = 2 * SizeVariants.MAX_FITTING_PROBES) {
    private val entries =
        object : LinkedHashMap<Long, Any>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Any>): Boolean = size > limit
        }

    /** The variant W×H. On the first lookup, [load] gives it, or `null` if it does not exist. */
    @Suppress("UNCHECKED_CAST")
    fun get(width: Int, height: Int, load: () -> V?): V? {
        val key = SizeVariants.key(width, height)
        synchronized(entries) {
            entries[key]?.let {
                return if (it === MISSING) null else it as V
            }
        }
        // the load can read a resource, so it runs without the lock
        val value = load()
        synchronized(entries) { entries[key] = value ?: MISSING }
        return value
    }

    private companion object {
        val MISSING = Any()
    }
}
