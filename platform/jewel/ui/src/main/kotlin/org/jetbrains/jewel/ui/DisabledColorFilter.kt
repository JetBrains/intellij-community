package org.jetbrains.jewel.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

// Implements javax.swing.GrayFilter's behaviour with percent = 50, brighter = true
// to match the GrayFilter#createDisabledImage behavior, used by Swing.
private val disabledColorMatrixGammaEncoded =
    ColorMatrix().apply {
        val saturation = .5f

        // We use NTSC luminance weights like Swing does as it's gamma-encoded RGB,
        // and add some brightness to emulate Swing's "brighter" approach, which is
        // not representable with a ColorMatrix alone as it's a non-linear op.
        val redFactor = .299f * saturation + .25f
        val greenFactor = .587f * saturation + .25f
        val blueFactor = .114f * saturation + .25f
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

public fun ColorFilter.Companion.disabled(): ColorFilter = colorMatrix(disabledColorMatrixGammaEncoded)
