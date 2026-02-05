// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi
import kotlin.math.roundToInt

@ExperimentalIconsApi
@Serializable
sealed interface Color {
  fun toHex(): String

  companion object {
    val Transparent: Color = RGBA(0f, 0f, 0f, 0f)
  }
}


@ExperimentalIconsApi
@Serializable
class RGBA(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
): Color {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RGBA

    if (red != other.red) return false
    if (green != other.green) return false
    if (blue != other.blue) return false
    if (alpha != other.alpha) return false

    return true
  }

  override fun hashCode(): Int {
    var result = red.hashCode()
    result = 31 * result + green.hashCode()
    result = 31 * result + blue.hashCode()
    result = 31 * result + alpha.hashCode()
    return result
  }

  override fun toString(): String {
    return "RGBA(red=$red, green=$green, blue=$blue, alpha=$alpha)"
  }

  override fun toHex(): String {
    val r = Integer.toHexString((red * 255).roundToInt())
    val g = Integer.toHexString((green * 255).roundToInt())
    val b = Integer.toHexString((blue * 255).roundToInt())
    val intAlpha = (alpha * 255).roundToInt()

    return formatColorRgbaHexString(r, g, b, intAlpha, true, true)
  }

  private fun formatColorRgbaHexString(
    rString: String,
    gString: String,
    bString: String,
    alphaInt: Int,
    includeHashSymbol: Boolean,
    omitAlphaWhenFullyOpaque: Boolean,
  ): String = buildString {
    if (includeHashSymbol) append('#')

    append(rString.padStart(2, '0'))
    append(gString.padStart(2, '0'))
    append(bString.padStart(2, '0'))

    if (alphaInt < 255 || !omitAlphaWhenFullyOpaque) {
      val a = Integer.toHexString(alphaInt)
      append(a.padStart(2, '0'))
    }
  }

}