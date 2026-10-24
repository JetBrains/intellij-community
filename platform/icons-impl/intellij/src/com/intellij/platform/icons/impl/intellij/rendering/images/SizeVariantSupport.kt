// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering.images

import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * `@WxH` size variants (`icon@20x20.svg` next to `icon.svg`), chosen per [SizeVariantPolicy]:
 *
 * - [SizeVariantPolicy.Exact] (default), at parity with the legacy `loadIconCustomVersion`
 *   (platform/core-ui/src/ui/icons/customIconUtil.kt): the logical painted size L (device pixels ÷ system scale; it contains
 *   the IDE zoom) must be an integer (within [TOLERANCE], to absorb float rounding of the scale factors), differ from the
 *   image's own logical size, and equal `JBUIScale.scale(W)` for an integer design size W — exactly the size the legacy
 *   check accepts (`foundIcon.iconWidth == JBUIScale.scale(width)`). Width and height are matched independently.
 * - [SizeVariantPolicy.Fitting]: the largest existing variant not larger than the design size (L ÷ IDE zoom) and larger
 *   than the base artwork, found by probing at most [MAX_FITTING_PROBES] sizes downward from the design size.
 * - [SizeVariantPolicy.None]: no variants.
 */
internal object SizeVariantSupport {
  private const val TOLERANCE = 0.01

  /** Upper bound of resource probes per lookup for [SizeVariantPolicy.Fitting] (two per size: square, then aspect). */
  const val MAX_FITTING_PROBES: Int = 16

  /**
   * The design size W×H of the variant to draw, or `null` to draw the base artwork. [exists] probes whether the `@WxH`
   * resource exists. [baseDesignWidth] × [baseDesignHeight] is the base artwork's design size, [ownLogicalWidth] ×
   * [ownLogicalHeight] the logical size it is painted at without scaling.
   */
  fun chooseVariant(
    policy: SizeVariantPolicy,
    logicalWidth: Double,
    logicalHeight: Double,
    ownLogicalWidth: Int,
    ownLogicalHeight: Int,
    baseDesignWidth: Int,
    baseDesignHeight: Int,
    exists: (width: Int, height: Int) -> Boolean,
  ): Pair<Int, Int>? = when (policy) {
    SizeVariantPolicy.None -> null
    SizeVariantPolicy.Exact -> designSize(logicalWidth, logicalHeight, ownLogicalWidth, ownLogicalHeight)
      ?.takeIf { (width, height) -> (width != baseDesignWidth || height != baseDesignHeight) && exists(width, height) }
    SizeVariantPolicy.Fitting -> fitting(logicalWidth, logicalHeight, baseDesignWidth, baseDesignHeight, exists)
  }

  /**
   * Probes N from floor(design width) down to base width + 1: first `@NxN` (square), then `@NxM` with M the height for the
   * base aspect ratio, each only if it fits the design height; at most [MAX_FITTING_PROBES] probes.
   */
  private fun fitting(
    logicalWidth: Double,
    logicalHeight: Double,
    baseWidth: Int,
    baseHeight: Int,
    exists: (Int, Int) -> Boolean,
  ): Pair<Int, Int>? {
    if (baseWidth <= 0 || baseHeight <= 0) return null
    val zoom = JBUIScale.scale(1f).toDouble()
    val designWidth = floor(logicalWidth / zoom + TOLERANCE).toInt()
    val designHeight = floor(logicalHeight / zoom + TOLERANCE).toInt()
    var probes = 0
    var width = designWidth
    while (width > baseWidth && probes < MAX_FITTING_PROBES) {
      val candidates = listOf(width to width, width to (width.toDouble() * baseHeight / baseWidth).roundToInt()).distinct()
      for ((w, h) in candidates) {
        if (h <= baseHeight || h > designHeight || probes >= MAX_FITTING_PROBES) continue
        probes++
        if (exists(w, h)) return w to h
      }
      width--
    }
    return null
  }

  /** Exact design size the image is painted at, or `null` if no variant lookup applies (fractional or own size). */
  private fun designSize(logicalWidth: Double, logicalHeight: Double, ownLogicalWidth: Int, ownLogicalHeight: Int): Pair<Int, Int>? {
    val width = integer(logicalWidth) ?: return null
    val height = integer(logicalHeight) ?: return null
    if (width <= 0 || height <= 0 || (width == ownLogicalWidth && height == ownLogicalHeight)) return null
    val designWidth = designFor(width) ?: return null
    val designHeight = designFor(height) ?: return null
    return designWidth to designHeight
  }

  private fun integer(value: Double): Int? {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) <= TOLERANCE) rounded else null
  }

  /** The integer design size whose `JBUIScale.scale` is [logical] (the IDE zoom divided out), if any. */
  private fun designFor(logical: Int): Int? {
    val zoom = JBUIScale.scale(1f).toDouble()
    val exact = logical / zoom
    return listOf(floor(exact).toInt(), ceil(exact).toInt())
      .distinct()
      .filter { it > 0 && JBUIScale.scale(it) == logical }
      .minByOrNull { abs(it - exact) }
  }

  /** `<path minus .svg>@WxH.svg` if [path] is an SVG and that resource exists in [classLoader], else `null`. */
  fun existingVariantPath(path: String, classLoader: ClassLoader, width: Int, height: Int): String? {
    if (!path.endsWith(".svg")) return null
    val variant = "${path.removeSuffix(".svg")}@${width}x$height.svg"
    return if (classLoader.getResource(variant.removePrefix("/")) != null) variant else null
  }

  /** [scale] with the object scale that draws a [designWidth]-wide variant into the box of logical width [logicalWidth]. */
  fun variantScale(scale: ScaleContext, logicalWidth: Double, designWidth: Int): ScaleContext {
    val zoom = scale.getScale(ScaleType.USR_SCALE)
    return ScaleContext.of(arrayOf(
      ScaleType.OBJ_SCALE.of(logicalWidth / (designWidth * zoom)),
      ScaleType.USR_SCALE.of(zoom),
      ScaleType.SYS_SCALE.of(scale.getScale(ScaleType.SYS_SCALE)),
    ))
  }

  /** Logical size (device pixels ÷ system scale) of an image whose expected size is [expected] under [scale]. */
  fun logicalSize(expected: Int, scale: ScaleContext): Double =
    expected * scale.getScale(ScaleType.OBJ_SCALE) * scale.getScale(ScaleType.USR_SCALE)
}
