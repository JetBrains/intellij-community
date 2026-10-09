// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.OnOffButtonState

/** Combines the colors and metrics that define the visual style of an on/off button (a.k.a. toggle). */
@Immutable
@GenerateDataFunctions
public class OnOffButtonStyle(
    /** The color tokens for the on/off button. */
    public val colors: OnOffButtonColors,
    /** The size and spacing metrics for the on/off button. */
    public val metrics: OnOffButtonMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnOffButtonStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "OnOffButtonStyle(colors=$colors, metrics=$metrics)"

    /** Companion object for [OnOffButtonStyle]. */
    public companion object
}

/**
 * Holds color tokens for the on/off button track, its border, its notch, and its focus ring, in the on, off, and
 * disabled states.
 */
@Immutable
@GenerateDataFunctions
public class OnOffButtonColors(
    /** The track fill color when the button is on. */
    public val backgroundOn: Color,
    /** The track fill color when the button is on and disabled. */
    public val backgroundOnDisabled: Color,
    /** The track fill color when the button is off. */
    public val backgroundOff: Color,
    /** The track fill color when the button is off and disabled. */
    public val backgroundOffDisabled: Color,
    /** The track border color when the button is on. */
    public val borderOn: Color,
    /** The track border color when the button is on and disabled. */
    public val borderOnDisabled: Color,
    /** The track border color when the button is off. */
    public val borderOff: Color,
    /** The track border color when the button is off and disabled. */
    public val borderOffDisabled: Color,
    /** The color of the focus ring drawn around the track. */
    public val borderFocused: Color,
    /** The notch color when the button is on. */
    public val notchOn: Color,
    /** The notch color when the button is on and disabled. */
    public val notchOnDisabled: Color,
    /** The notch color when the button is off. */
    public val notchOff: Color,
    /** The notch color when the button is off and disabled. */
    public val notchOffDisabled: Color,
) {
    /** Returns a [State] holding the track fill color appropriate for the given [state]. */
    @Composable
    public fun backgroundFor(state: OnOffButtonState): State<Color> =
        rememberUpdatedState(
            choose(
                state,
                on = backgroundOn,
                onDisabled = backgroundOnDisabled,
                off = backgroundOff,
                offDisabled = backgroundOffDisabled,
            )
        )

    /** Returns a [State] holding the track border color appropriate for the given [state]. */
    @Composable
    public fun borderFor(state: OnOffButtonState): State<Color> =
        rememberUpdatedState(
            choose(
                state,
                on = borderOn,
                onDisabled = borderOnDisabled,
                off = borderOff,
                offDisabled = borderOffDisabled,
            )
        )

    /** Returns a [State] holding the notch color appropriate for the given [state]. */
    @Composable
    public fun notchFor(state: OnOffButtonState): State<Color> =
        rememberUpdatedState(
            choose(state, on = notchOn, onDisabled = notchOnDisabled, off = notchOff, offDisabled = notchOffDisabled)
        )

    private fun choose(state: OnOffButtonState, on: Color, onDisabled: Color, off: Color, offDisabled: Color): Color =
        when {
            state.isSelected && state.isEnabled -> on
            state.isSelected -> onDisabled
            state.isEnabled -> off
            else -> offDisabled
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnOffButtonColors

        if (backgroundOn != other.backgroundOn) return false
        if (backgroundOnDisabled != other.backgroundOnDisabled) return false
        if (backgroundOff != other.backgroundOff) return false
        if (backgroundOffDisabled != other.backgroundOffDisabled) return false
        if (borderOn != other.borderOn) return false
        if (borderOnDisabled != other.borderOnDisabled) return false
        if (borderOff != other.borderOff) return false
        if (borderOffDisabled != other.borderOffDisabled) return false
        if (borderFocused != other.borderFocused) return false
        if (notchOn != other.notchOn) return false
        if (notchOnDisabled != other.notchOnDisabled) return false
        if (notchOff != other.notchOff) return false
        if (notchOffDisabled != other.notchOffDisabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backgroundOn.hashCode()
        result = 31 * result + backgroundOnDisabled.hashCode()
        result = 31 * result + backgroundOff.hashCode()
        result = 31 * result + backgroundOffDisabled.hashCode()
        result = 31 * result + borderOn.hashCode()
        result = 31 * result + borderOnDisabled.hashCode()
        result = 31 * result + borderOff.hashCode()
        result = 31 * result + borderOffDisabled.hashCode()
        result = 31 * result + borderFocused.hashCode()
        result = 31 * result + notchOn.hashCode()
        result = 31 * result + notchOnDisabled.hashCode()
        result = 31 * result + notchOff.hashCode()
        result = 31 * result + notchOffDisabled.hashCode()
        return result
    }

    override fun toString(): String {
        return "OnOffButtonColors(" +
            "backgroundOn=$backgroundOn, " +
            "backgroundOnDisabled=$backgroundOnDisabled, " +
            "backgroundOff=$backgroundOff, " +
            "backgroundOffDisabled=$backgroundOffDisabled, " +
            "borderOn=$borderOn, " +
            "borderOnDisabled=$borderOnDisabled, " +
            "borderOff=$borderOff, " +
            "borderOffDisabled=$borderOffDisabled, " +
            "borderFocused=$borderFocused, " +
            "notchOn=$notchOn, " +
            "notchOnDisabled=$notchOnDisabled, " +
            "notchOff=$notchOff, " +
            "notchOffDisabled=$notchOffDisabled" +
            ")"
    }

    /** Companion object for [OnOffButtonColors]. */
    public companion object
}

/**
 * Holds size metrics for the on/off button: the track, its border, the notch in each state, and the focus ring.
 *
 * The notch is vertically centered in the track, and kept as far from the track edge as it is from the top and bottom.
 */
@Immutable
@GenerateDataFunctions
public class OnOffButtonMetrics(
    /** The size of the track. */
    public val trackSize: DpSize,
    /** The corner size of the track, its border, and the focus ring. */
    public val trackCornerSize: CornerSize,
    /** The width of the border drawn inside the track. */
    public val borderWidth: Dp,
    /** The size of the notch when the button is on. The "on" notch is filled. */
    public val notchSizeOn: DpSize,
    /** The corner size of the notch when the button is on. */
    public val notchCornerSizeOn: CornerSize,
    /** The outer size of the notch when the button is off. The "off" notch is a ring. */
    public val notchSizeOff: DpSize,
    /** The corner size of the notch when the button is off. */
    public val notchCornerSizeOff: CornerSize,
    /** The stroke width of the ring-shaped notch shown when the button is off. */
    public val notchStrokeWidthOff: Dp,
    /** The width of the focus ring. */
    public val focusOutlineWidth: Dp,
    /** The gap between the track and the focus ring. */
    public val focusOutlineExpand: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnOffButtonMetrics

        if (trackSize != other.trackSize) return false
        if (trackCornerSize != other.trackCornerSize) return false
        if (borderWidth != other.borderWidth) return false
        if (notchSizeOn != other.notchSizeOn) return false
        if (notchCornerSizeOn != other.notchCornerSizeOn) return false
        if (notchSizeOff != other.notchSizeOff) return false
        if (notchCornerSizeOff != other.notchCornerSizeOff) return false
        if (notchStrokeWidthOff != other.notchStrokeWidthOff) return false
        if (focusOutlineWidth != other.focusOutlineWidth) return false
        if (focusOutlineExpand != other.focusOutlineExpand) return false

        return true
    }

    override fun hashCode(): Int {
        var result = trackSize.hashCode()
        result = 31 * result + trackCornerSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + notchSizeOn.hashCode()
        result = 31 * result + notchCornerSizeOn.hashCode()
        result = 31 * result + notchSizeOff.hashCode()
        result = 31 * result + notchCornerSizeOff.hashCode()
        result = 31 * result + notchStrokeWidthOff.hashCode()
        result = 31 * result + focusOutlineWidth.hashCode()
        result = 31 * result + focusOutlineExpand.hashCode()
        return result
    }

    override fun toString(): String {
        return "OnOffButtonMetrics(" +
            "trackSize=$trackSize, " +
            "trackCornerSize=$trackCornerSize, " +
            "borderWidth=$borderWidth, " +
            "notchSizeOn=$notchSizeOn, " +
            "notchCornerSizeOn=$notchCornerSizeOn, " +
            "notchSizeOff=$notchSizeOff, " +
            "notchCornerSizeOff=$notchCornerSizeOff, " +
            "notchStrokeWidthOff=$notchStrokeWidthOff, " +
            "focusOutlineWidth=$focusOutlineWidth, " +
            "focusOutlineExpand=$focusOutlineExpand" +
            ")"
    }

    /** Companion object for [OnOffButtonMetrics]. */
    public companion object
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackOnOffButtonStyle(): OnOffButtonStyle {
    val colors =
        OnOffButtonColors(
            backgroundOn = Color(0xFF3871E1),
            backgroundOnDisabled = Color(0xFFF7F8F9),
            backgroundOff = Color(0x30000000),
            backgroundOffDisabled = Color(0xFFF7F8F9),
            borderOn = Color(0xFF3871E1),
            borderOnDisabled = Color(0xFFDDDFE4),
            borderOff = Color(0x00FFFFFF),
            borderOffDisabled = Color(0xFFDDDFE4),
            borderFocused = Color(0xFF3871E1),
            notchOn = Color(0xFFFFFFFF),
            notchOnDisabled = Color(0xFFC3C5CB),
            notchOff = Color(0xFFFFFFFF),
            notchOffDisabled = Color(0xFFC3C5CB),
        )

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

/** Provides the [OnOffButtonStyle] to use for on/off buttons in the current composition. */
public val LocalOnOffButtonStyle: ProvidableCompositionLocal<OnOffButtonStyle> = staticCompositionLocalOf {
    error("No OnOffButtonStyle provided. Have you forgotten the theme?")
}
