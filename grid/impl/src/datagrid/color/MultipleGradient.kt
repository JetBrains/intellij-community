package com.intellij.database.datagrid.color

import java.awt.Color

/**
 * Inspired by [java.awt.MultipleGradientPaintContext]
 * Used for getting multiple gradient values.
 * <pre>{@code
 * @Suppress("UseJBColor")
 * val multipleGradient by lazy { MultipleGradient(floatArrayOf(0f, 0.5f, 1f), arrayOf(Color.BLUE, Color.ORANGE, Color.RED)) }
 *
 * fun foo() {
 *   multipleGradient.getColor(0.3f)
 * }
 * }</pre>
 */
class MultipleGradient(private val fractions: FloatArray, colors: Array<Color>) {

  private var normalizedIntervals = Array(fractions.size - 1) { index ->
    fractions[index + 1] - fractions[index]
  }

  private var gradients = Array(normalizedIntervals.size){ index ->
    interpolate(colors[index].rgb, colors[index + 1].rgb)
  }

  /** Position should be in range [0,1] or will be truncated to this range. */
  fun getColor(position: Float): Color {
    val pos = position.coerceIn(0f, 1f)
    for (i in gradients.indices) {
      if (pos < fractions[i + 1]) {
        val delta = pos - fractions[i]
        val index = ((delta / normalizedIntervals[i]) * GRADIENT_SIZE_INDEX).toInt()
        return gradients[i][index]
      }
    }
    return gradients[gradients.size - 1][GRADIENT_SIZE_INDEX]
  }

  private fun interpolate(rgb1: Int, rgb2: Int) : Array<Color> {
    val a1 = rgb1 shr 24 and 0xff
    val r1 = rgb1 shr 16 and 0xff
    val g1 = rgb1 shr 8 and 0xff
    val b1 = rgb1 and 0xff

    val da = ((rgb2 shr 24) and 0xff) - a1
    val dr = ((rgb2 shr 16) and 0xff) - r1
    val dg = ((rgb2 shr 8) and 0xff) - g1
    val db = ((rgb2) and 0xff) - b1

    val stepSize = 1.0f / GRADIENT_SIZE

    return Array(GRADIENT_SIZE) { i ->
      val intColor = ((((a1 + i * da * stepSize) + 0.5).toInt() shl 24)) or
        ((((r1 + i * dr * stepSize) + 0.5).toInt() shl 16)) or
        ((((g1 + i * dg * stepSize) + 0.5).toInt() shl 8)) or ((((b1 + i * db * stepSize) + 0.5).toInt()))
      @Suppress("UseJBColor") // Gradient is theme-aware.
      Color(intColor)
    }
  }

  companion object {
    const val GRADIENT_SIZE: Int = 256
    const val GRADIENT_SIZE_INDEX: Int = GRADIENT_SIZE - 1
  }
}