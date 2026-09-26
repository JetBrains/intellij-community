// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.SRGBColor
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
class DefaultSRGB(
    override val red: Float,
    override val green: Float,
    override val blue: Float,
    override val alpha: Float,
) : SRGBColor {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultSRGB

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

    override fun toString(): String = "RGBA(red=$red, green=$green, blue=$blue, alpha=$alpha)"

    override fun toHex(): String {
        val r = Integer.toHexString((red * 255).roundToInt())
        val g = Integer.toHexString((green * 255).roundToInt())
        val b = Integer.toHexString((blue * 255).roundToInt())
        val intAlpha = (alpha * 255).roundToInt()

        return formatColorRgbaHexString(r, g, b, intAlpha, true, true)
    }

    @Suppress("SameParameterValue")
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

    companion object {
        fun fromHex(hex: String): SRGBColor {
            val result = hexRegex.matchEntire(hex) ?: throw IllegalArgumentException("Invalid hex color: $hex")
            val (r, g, b, a) = result.destructured
            return DefaultSRGB(
                red = r.toInt(16) / 255f,
                green = g.toInt(16) / 255f,
                blue = b.toInt(16) / 255f,
                alpha = if (a == "") 1f else a.toInt(16) / 255f,
            )
        }

        private val hexRegex = Regex("#?([0-9A-F]{2})([0-9A-F]{2})([0-9A-F]{2})([0-9A-F]{2})?")
    }
}
