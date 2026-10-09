// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.OnOffButtonColors
import org.jetbrains.jewel.ui.component.styling.OnOffButtonMetrics
import org.jetbrains.jewel.ui.component.styling.OnOffButtonStyle

internal fun readDefaultOnOffButtonStyle(): OnOffButtonStyle {
    val colors =
        OnOffButtonColors(
            backgroundOn = retrieveColorOrUnspecified("ColorPalette.toggle-on-bg"),
            backgroundOnDisabled = retrieveColorOrUnspecified("ColorPalette.toggle-on-disabled-bg"),
            backgroundOff = retrieveColorOrUnspecified("ColorPalette.toggle-off-bg"),
            backgroundOffDisabled = retrieveColorOrUnspecified("ColorPalette.toggle-off-disabled-bg"),
            borderOn = retrieveColorOrUnspecified("ColorPalette.toggle-on-border"),
            borderOnDisabled = retrieveColorOrUnspecified("ColorPalette.toggle-on-disabled-border"),
            borderOff = retrieveColorOrUnspecified("ColorPalette.toggle-off-border"),
            borderOffDisabled = retrieveColorOrUnspecified("ColorPalette.toggle-off-disabled-border"),
            borderFocused = retrieveColorOrUnspecified("ColorPalette.toggle-focus-border"),
            notchOn = retrieveColorOrUnspecified("ColorPalette.toggle-on-notch"),
            notchOnDisabled = retrieveColorOrUnspecified("ColorPalette.toggle-on-disabled-notch"),
            notchOff = retrieveColorOrUnspecified("ColorPalette.toggle-off-notch"),
            notchOffDisabled = retrieveColorOrUnspecified("ColorPalette.toggle-off-disabled-notch"),
        )

    // IJP renders SVGs. These values were taken directly from the SVGs, translated them to Compose values.
    // The SVGs are located at platform/platform-impl/resources/com/intellij/ide/ui/laf/icons
    val metrics =
        OnOffButtonMetrics(
            trackSize = DpSize(26.dp, 16.dp),
            trackCornerSize = CornerSize(8.dp),
            borderWidth = 1.dp,
            notchSizeOn = DpSize(10.dp, 10.dp),
            notchCornerSizeOn = CornerSize(50),
            notchSizeOff = DpSize(8.dp, 8.dp),
            notchCornerSizeOff = CornerSize(50),
            notchStrokeWidthOff = 2.dp,
            focusOutlineWidth = 2.dp,
            focusOutlineExpand = 1.dp,
        )

    return OnOffButtonStyle(colors, metrics)
}
