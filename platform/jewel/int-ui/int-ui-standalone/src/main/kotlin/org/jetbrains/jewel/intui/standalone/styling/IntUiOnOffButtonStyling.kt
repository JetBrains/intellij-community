// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.OnOffButtonColors
import org.jetbrains.jewel.ui.component.styling.OnOffButtonMetrics
import org.jetbrains.jewel.ui.component.styling.OnOffButtonStyle

/**
 * Creates an [OnOffButtonStyle] with the Islands Light theme defaults.
 *
 * @param colors The color scheme for the on/off button.
 * @param metrics The size and spacing metrics for the on/off button.
 */
public fun OnOffButtonStyle.Companion.light(
    colors: OnOffButtonColors = OnOffButtonColors.light(),
    metrics: OnOffButtonMetrics = OnOffButtonMetrics.default(),
): OnOffButtonStyle = OnOffButtonStyle(colors, metrics)

/**
 * Creates an [OnOffButtonStyle] with the Islands Dark theme defaults.
 *
 * @param colors The color scheme for the on/off button.
 * @param metrics The size and spacing metrics for the on/off button.
 */
public fun OnOffButtonStyle.Companion.dark(
    colors: OnOffButtonColors = OnOffButtonColors.dark(),
    metrics: OnOffButtonMetrics = OnOffButtonMetrics.default(),
): OnOffButtonStyle = OnOffButtonStyle(colors, metrics)

/** Creates [OnOffButtonColors] with the `toggle-*` colors of the Islands Light theme. */
public fun OnOffButtonColors.Companion.light(
    backgroundOn: Color = Color(0xFF3871E1),
    backgroundOnDisabled: Color = Color(0xFFF7F8F9),
    backgroundOff: Color = Color(0x30000000),
    backgroundOffDisabled: Color = Color(0xFFF7F8F9),
    borderOn: Color = Color(0xFF3871E1),
    borderOnDisabled: Color = Color(0xFFDDDFE4),
    borderOff: Color = Color(0x00FFFFFF),
    borderOffDisabled: Color = Color(0xFFDDDFE4),
    borderFocused: Color = Color(0xFF3871E1),
    notchOn: Color = Color(0xFFFFFFFF),
    notchOnDisabled: Color = Color(0xFFC3C5CB),
    notchOff: Color = Color(0xFFFFFFFF),
    notchOffDisabled: Color = Color(0xFFC3C5CB),
): OnOffButtonColors =
    OnOffButtonColors(
        backgroundOn = backgroundOn,
        backgroundOnDisabled = backgroundOnDisabled,
        backgroundOff = backgroundOff,
        backgroundOffDisabled = backgroundOffDisabled,
        borderOn = borderOn,
        borderOnDisabled = borderOnDisabled,
        borderOff = borderOff,
        borderOffDisabled = borderOffDisabled,
        borderFocused = borderFocused,
        notchOn = notchOn,
        notchOnDisabled = notchOnDisabled,
        notchOff = notchOff,
        notchOffDisabled = notchOffDisabled,
    )

/** Creates [OnOffButtonColors] with the `toggle-*` colors of the Islands Dark theme. */
public fun OnOffButtonColors.Companion.dark(
    backgroundOn: Color = Color(0xFF3871E1),
    backgroundOnDisabled: Color = Color(0x00191A1C),
    backgroundOff: Color = Color(0x3BFFFFFF),
    backgroundOffDisabled: Color = Color(0x00191A1C),
    borderOn: Color = Color(0xFF3871E1),
    borderOnDisabled: Color = Color(0xFF33353B),
    borderOff: Color = Color(0x00191A1C),
    borderOffDisabled: Color = Color(0xFF33353B),
    borderFocused: Color = Color(0xFF3871E1),
    notchOn: Color = Color(0xFFFFFFFF),
    notchOnDisabled: Color = Color(0xFF5F6269),
    notchOff: Color = Color(0xFFD1D3D9),
    notchOffDisabled: Color = Color(0xFF5F6269),
): OnOffButtonColors =
    OnOffButtonColors(
        backgroundOn = backgroundOn,
        backgroundOnDisabled = backgroundOnDisabled,
        backgroundOff = backgroundOff,
        backgroundOffDisabled = backgroundOffDisabled,
        borderOn = borderOn,
        borderOnDisabled = borderOnDisabled,
        borderOff = borderOff,
        borderOffDisabled = borderOffDisabled,
        borderFocused = borderFocused,
        notchOn = notchOn,
        notchOnDisabled = notchOnDisabled,
        notchOff = notchOff,
        notchOffDisabled = notchOffDisabled,
    )

/**
 * Creates [OnOffButtonMetrics] matching the geometry of the IntelliJ Platform `toggle*.svg` assets.
 *
 * @param trackSize The size of the track.
 * @param trackCornerSize The corner size of the track, its border, and the focus ring.
 * @param borderWidth The width of the border drawn inside the track.
 * @param notchSizeOn The size of the filled notch shown when the button is on.
 * @param notchCornerSizeOn The corner size of the notch when the button is on.
 * @param notchSizeOff The outer size of the ring-shaped notch shown when the button is off.
 * @param notchCornerSizeOff The corner size of the notch when the button is off.
 * @param notchStrokeWidthOff The stroke width of the ring-shaped notch shown when the button is off.
 * @param focusOutlineWidth The width of the focus ring.
 * @param focusOutlineExpand The gap between the track and the focus ring.
 */
public fun OnOffButtonMetrics.Companion.default(
    trackSize: DpSize = DpSize(26.dp, 16.dp),
    trackCornerSize: CornerSize = CornerSize(8.dp),
    borderWidth: Dp = 1.dp,
    notchSizeOn: DpSize = DpSize(10.dp, 10.dp),
    notchCornerSizeOn: CornerSize = CornerSize(50),
    notchSizeOff: DpSize = DpSize(8.dp, 8.dp),
    notchCornerSizeOff: CornerSize = CornerSize(50),
    notchStrokeWidthOff: Dp = 2.dp,
    focusOutlineWidth: Dp = 2.dp,
    focusOutlineExpand: Dp = 1.dp,
): OnOffButtonMetrics =
    OnOffButtonMetrics(
        trackSize = trackSize,
        trackCornerSize = trackCornerSize,
        borderWidth = borderWidth,
        notchSizeOn = notchSizeOn,
        notchCornerSizeOn = notchCornerSizeOn,
        notchSizeOff = notchSizeOff,
        notchCornerSizeOff = notchCornerSizeOff,
        notchStrokeWidthOff = notchStrokeWidthOff,
        focusOutlineWidth = focusOutlineWidth,
        focusOutlineExpand = focusOutlineExpand,
    )
