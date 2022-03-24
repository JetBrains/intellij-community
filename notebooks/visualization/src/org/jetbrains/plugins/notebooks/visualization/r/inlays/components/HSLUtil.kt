/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import kotlin.math.max
import kotlin.math.min

/**
 * The original source code was posted here: https://tips4java.wordpress.com/2009/07/05/hsl-color/
 *
 * According to https://tips4java.wordpress.com/about/
 * "We assume no responsibility for the code. You are free to use and/or modify and/or distribute any or all code posted
 *  on the Java Tips Weblog without restriction. A credit in the code comments would be nice, but not in any way mandatory."
 */

/**
 * Convert RGB floats to HSL floats.
 *
 * @param rgb input rgb floats from 0 to 1.
 * @param result output hsl floats, Hue is from 0 to 360, s and l are from 0 to 100.
 */
fun convertRGBtoHSL(rgb: FloatArray, result: FloatArray) { //  Get RGB values in the range 0 - 1
  val r = rgb[0]
  val g = rgb[1]
  val b = rgb[2]
  //	Minimum and Maximum RGB values are used in the HSL calculations
  val min = min(r, min(g, b))
  val max = max(r, max(g, b))
  //  Calculate the Hue
  var h = 0f
  if (max == min) h = 0f else if (max == r) h = (60 * (g - b) / (max - min) + 360) % 360 else if (max == g) h = 60 * (b - r) / (max - min) + 120 else if (max == b) h = 60 * (r - g) / (max - min) + 240
  //  Calculate the Luminance
  val l = (max + min) / 2
  //  Calculate the Saturation
  var s = 0f
  s = if (max == min) 0f else if (l <= .5f) (max - min) / (max + min) else (max - min) / (2 - max - min)
  result[0] = h
  result[1] = s * 100
  result[2] = l * 100
}


/**
 * Convert HSL values to an RGB value.
 *
 * @param hslFloats  hsl floats, Hue is from 0 to 360, s and l are from 0 to 100.
 * @param alpha  the alpha value between 0 - 1
 *
 * @returns the integer RGB value
 */
fun convertHSLtoRGB(hslFloats: FloatArray, alpha: Float): Int {
  var h = hslFloats[0]
  var s = hslFloats[1]
  var l = hslFloats[2]
  if (s < 0.0f || s > 100.0f) {
    val message = "Color parameter outside of expected range - Saturation"
    throw IllegalArgumentException(message)
  }
  if (l < 0.0f || l > 100.0f) {
    val message = "Color parameter outside of expected range - Luminance"
    throw IllegalArgumentException(message)
  }
  if (alpha < 0.0f || alpha > 1.0f) {
    val message = "Color parameter outside of expected range - Alpha"
    throw IllegalArgumentException(message)
  }
  //  Formula needs all values between 0 - 1.
  h = h % 360.0f
  h /= 360f
  s /= 100f
  l /= 100f
  var q = 0f
  q = if (l < 0.5) l * (1 + s) else l + s - s * l
  val p = 2 * l - q
  var r = max(0f, hueToRGB(p, q, h + 1.0f / 3.0f))
  var g = max(0f, hueToRGB(p, q, h))
  var b = max(0f, hueToRGB(p, q, h - 1.0f / 3.0f))
  r = min(r, 1.0f)
  g = min(g, 1.0f)
  b = min(b, 1.0f)
  return ((alpha * 255).toInt() shl 24) + ((r * 255).toInt() shl 16) + ((g * 255).toInt() shl 8) + (b * 255).toInt()
}

private fun hueToRGB(p: Float, q: Float, h: Float): Float {
  var h = h
  if (h < 0) h += 1f
  if (h > 1) h -= 1f
  if (6 * h < 1) {
    return p + (q - p) * 6 * h
  }
  if (2 * h < 1) {
    return q
  }
  return if (3 * h < 2) {
    p + (q - p) * 6 * (2.0f / 3.0f - h)
  } else p
}