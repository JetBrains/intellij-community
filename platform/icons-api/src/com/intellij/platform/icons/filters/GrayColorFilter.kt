// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.filters

import com.intellij.platform.icons.IconManager

/**
 * A filter that turns an image gray. IntelliJ themes use it for the look of a disabled icon.
 *
 * For each pixel, the filter does these steps, in this order. Each step truncates its result to an integer.
 * 1. It computes the NTSC luminance of the red, green and blue values (`0..255`, not converted to linear light):
 *    `gray = 0.30 * red + 0.59 * green + 0.11 * blue`.
 * 2. It applies the brightness. The brightness curve is cubic: `b = brightness^3 / 10000`, so `b` is in `-100..100`.
 *    If `b >= 0`, then `gray = (gray + b * 255) / (1 + b)`. Otherwise, `gray = gray / (1 - b)`.
 * 3. It applies the contrast `c = contrast / 100`.
 *    If `c < 0`, then `gray = 127 + (gray - 127) * (1 + c)`. This moves the gray toward 127.
 *    If `c >= 0`, then `gray = gray + (255 - gray) * c` for a gray of 127 or more,
 *    and `gray = gray - gray * c` for a lower gray. This moves the gray toward the nearest end of the range.
 *    The two cases give different results at 127, so the curve has a jump there.
 * 4. It multiplies the alpha of the pixel by `alpha / 100`.
 *
 * The red, green and blue values of the result are all equal to `gray`.
 *
 * Every renderer must give this look, so a disabled icon looks the same in Swing and in Compose.
 * Swing uses the legacy `GrayFilter`, which follows these steps exactly.
 * A renderer that rounds instead of truncating can differ by one level.
 * The values outside the ranges are clamped.
 *
 * @param brightness in the range `-100..100`, where `0` has no effect
 * @param contrast in the range `-100..100`, where `0` has no effect
 * @param alpha in the range `0..100`, where `0` is transparent and `100` has no effect
 */
fun ColorFilter.Companion.gray(brightness: Int, contrast: Int, alpha: Int): ColorFilter =
    IconManager.colorFilters().gray(brightness, contrast, alpha)
