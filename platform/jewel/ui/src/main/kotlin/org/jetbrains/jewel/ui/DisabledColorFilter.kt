package org.jetbrains.jewel.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import org.jetbrains.jewel.foundation.theme.JewelTheme

// Implements javax.swing.GrayFilter's behaviour with percent = 50, brighter = true
// to match the GrayFilter#createDisabledImage behavior, used by Swing.
private fun disabledColorMatrixGammaEncoded(isSystemInDarkMode: Boolean = false) =
    ColorMatrix().apply {
        val saturation = .5f
        val brightness = if (isSystemInDarkMode) .125f else .25f

        // We use NTSC luminance weights like Swing does as it's gamma-encoded RGB,
        // and add some brightness to emulate Swing's "brighter" approach, which is
        // not representable with a ColorMatrix alone as it's a non-linear op.
        val redFactor = .299f * saturation + brightness
        val greenFactor = .587f * saturation + brightness
        val blueFactor = .114f * saturation + brightness
        this[0, 0] = redFactor
        this[0, 1] = greenFactor
        this[0, 2] = blueFactor
        this[1, 0] = redFactor
        this[1, 1] = greenFactor
        this[1, 2] = blueFactor
        this[2, 0] = redFactor
        this[2, 1] = greenFactor
        this[2, 2] = blueFactor
    }

@Deprecated("Use disabledThemeAware instead. This way you get nice colors on dark theme too!")
public fun ColorFilter.Companion.disabled(): ColorFilter = colorMatrix(disabledColorMatrixGammaEncoded())

@Composable
public fun ColorFilter.Companion.disabledThemeAware(): ColorFilter =
    colorMatrix(disabledColorMatrixGammaEncoded(JewelTheme.isDark))
