package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.SegmentedControlButtonState

@Stable
@GenerateDataFunctions
public class SegmentedControlButtonStyle(
    public val colors: SegmentedControlButtonColors,
    public val metrics: SegmentedControlButtonMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlButtonStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "SegmentedControlButtonStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SegmentedControlButtonColors(
    public val background: Brush,
    public val backgroundPressed: Brush,
    public val backgroundHovered: Brush,
    public val backgroundSelected: Brush,
    public val backgroundSelectedFocused: Brush,
    public val content: Color,
    public val contentDisabled: Color,
    public val border: Brush,
    public val borderSelected: Brush,
    public val borderSelectedDisabled: Brush,
    public val borderSelectedFocused: Brush,
) {

    @Composable
    public fun contentFor(state: SegmentedControlButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> contentDisabled
                else -> content
            }
        )

    @Composable
    public fun backgroundFor(state: SegmentedControlButtonState, isFocused: Boolean): State<Brush> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> background
                state.isSelected && isFocused -> backgroundSelectedFocused
                state.isSelected -> backgroundSelected
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                else -> background
            }
        )

    @Composable
    public fun borderFor(state: SegmentedControlButtonState, isFocused: Boolean): State<Brush> =
        rememberUpdatedState(
            when {
                !state.isSelected -> border
                !state.isEnabled -> borderSelectedDisabled
                isFocused -> borderSelectedFocused
                else -> borderSelected
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlButtonColors

        if (background != other.background) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (backgroundSelectedFocused != other.backgroundSelectedFocused) return false
        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (border != other.border) return false
        if (borderSelected != other.borderSelected) return false
        if (borderSelectedDisabled != other.borderSelectedDisabled) return false
        if (borderSelectedFocused != other.borderSelectedFocused) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + backgroundSelectedFocused.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + borderSelected.hashCode()
        result = 31 * result + borderSelectedDisabled.hashCode()
        result = 31 * result + borderSelectedFocused.hashCode()
        return result
    }

    override fun toString(): String {
        return "SegmentedControlButtonColors(" +
            "background=$background, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "backgroundSelected=$backgroundSelected, " +
            "backgroundSelectedFocused=$backgroundSelectedFocused, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "border=$border, " +
            "borderSelected=$borderSelected, " +
            "borderSelectedDisabled=$borderSelectedDisabled, " +
            "borderSelectedFocused=$borderSelectedFocused" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class SegmentedControlButtonMetrics(
    public val cornerSize: CornerSize,
    public val segmentedButtonPadding: PaddingValues,
    public val minSize: DpSize,
    public val borderWidth: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentedControlButtonMetrics

        if (cornerSize != other.cornerSize) return false
        if (segmentedButtonPadding != other.segmentedButtonPadding) return false
        if (minSize != other.minSize) return false
        if (borderWidth != other.borderWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + segmentedButtonPadding.hashCode()
        result = 31 * result + minSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        return result
    }

    override fun toString(): String {
        return "SegmentedControlButtonMetrics(" +
            "cornerSize=$cornerSize, " +
            "segmentedButtonPadding=$segmentedButtonPadding, " +
            "minSize=$minSize, " +
            "borderWidth=$borderWidth" +
            ")"
    }

    public companion object
}

@Composable
private fun <T> SegmentedControlButtonState.chooseValueIgnoreCompat(
    normal: T,
    disabled: T,
    pressed: T,
    hovered: T,
    active: T,
): T =
    when {
        !isEnabled -> disabled
        isPressed -> pressed
        isHovered -> hovered
        isActive -> active
        else -> normal
    }

public val LocalSegmentedControlButtonStyle: ProvidableCompositionLocal<SegmentedControlButtonStyle> =
    staticCompositionLocalOf {
        error("No LocalSegmentedControlButtonStyle provided. Have you forgotten the theme?")
    }
