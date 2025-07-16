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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.IconButtonState
import org.jetbrains.jewel.ui.component.SelectableIconButtonState
import org.jetbrains.jewel.ui.component.ToggleableIconButtonState

@Stable
@GenerateDataFunctions
public class IconButtonStyle(public val colors: IconButtonColors, public val metrics: IconButtonMetrics) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IconButtonStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "IconButtonStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class IconButtonColors(
    public val foregroundSelectedActivated: Color,
    public val background: Color,
    public val backgroundDisabled: Color,
    public val backgroundSelected: Color,
    public val backgroundSelectedActivated: Color,
    public val backgroundFocused: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderSelected: Color,
    public val borderSelectedActivated: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
) {
    @Composable
    public fun backgroundFor(state: IconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isFocused -> backgroundFocused
                else -> background
            }
        )

    @Composable
    public fun selectableBackgroundFor(state: SelectableIconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isActive && state.isSelected -> backgroundSelectedActivated
                state.isSelected -> backgroundSelected
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isFocused -> backgroundFocused
                else -> background
            }
        )

    @Composable
    public fun toggleableBackgroundFor(state: ToggleableIconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> backgroundDisabled
                state.isActive && state.isSelected -> backgroundSelectedActivated
                state.isSelected -> backgroundSelected
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                state.isFocused -> backgroundFocused
                else -> background
            }
        )

    @Composable
    public fun selectableForegroundFor(state: SelectableIconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                state.isActive && state.isSelected -> foregroundSelectedActivated
                else -> Color.Unspecified
            }
        )

    @Composable
    public fun toggleableForegroundFor(state: ToggleableIconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                state.isActive && state.toggleableState == ToggleableState.On -> foregroundSelectedActivated
                else -> Color.Unspecified
            }
        )

    @Composable
    public fun borderFor(state: IconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> borderDisabled
                state.isFocused -> borderFocused
                state.isPressed -> borderPressed
                state.isHovered -> borderHovered
                else -> border
            }
        )

    @Composable
    public fun selectableBorderFor(state: SelectableIconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> borderDisabled
                state.isActive && state.isSelected -> borderSelectedActivated
                state.isSelected -> borderSelected
                state.isFocused -> borderFocused
                state.isPressed -> borderPressed
                state.isHovered -> borderHovered
                else -> border
            }
        )

    @Composable
    public fun toggleableBorderFor(state: ToggleableIconButtonState): State<Color> =
        rememberUpdatedState(
            when {
                !state.isEnabled -> borderDisabled
                state.isActive && state.isSelected -> borderSelectedActivated
                state.isSelected -> borderSelected
                state.isFocused -> borderFocused
                state.isPressed -> borderPressed
                state.isHovered -> borderHovered
                else -> border
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IconButtonColors

        if (foregroundSelectedActivated != other.foregroundSelectedActivated) return false
        if (background != other.background) return false
        if (backgroundDisabled != other.backgroundDisabled) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (backgroundSelectedActivated != other.backgroundSelectedActivated) return false
        if (backgroundFocused != other.backgroundFocused) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (border != other.border) return false
        if (borderDisabled != other.borderDisabled) return false
        if (borderSelected != other.borderSelected) return false
        if (borderSelectedActivated != other.borderSelectedActivated) return false
        if (borderFocused != other.borderFocused) return false
        if (borderPressed != other.borderPressed) return false
        if (borderHovered != other.borderHovered) return false

        return true
    }

    override fun hashCode(): Int {
        var result = foregroundSelectedActivated.hashCode()
        result = 31 * result + background.hashCode()
        result = 31 * result + backgroundDisabled.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + backgroundSelectedActivated.hashCode()
        result = 31 * result + backgroundFocused.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + borderDisabled.hashCode()
        result = 31 * result + borderSelected.hashCode()
        result = 31 * result + borderSelectedActivated.hashCode()
        result = 31 * result + borderFocused.hashCode()
        result = 31 * result + borderPressed.hashCode()
        result = 31 * result + borderHovered.hashCode()
        return result
    }

    override fun toString(): String {
        return "IconButtonColors(" +
            "foregroundSelectedActivated=$foregroundSelectedActivated, " +
            "background=$background, " +
            "backgroundDisabled=$backgroundDisabled, " +
            "backgroundSelected=$backgroundSelected, " +
            "backgroundSelectedActivated=$backgroundSelectedActivated, " +
            "backgroundFocused=$backgroundFocused, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "border=$border, " +
            "borderDisabled=$borderDisabled, " +
            "borderSelected=$borderSelected, " +
            "borderSelectedActivated=$borderSelectedActivated, " +
            "borderFocused=$borderFocused, " +
            "borderPressed=$borderPressed, " +
            "borderHovered=$borderHovered" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class IconButtonMetrics(
    public val cornerSize: CornerSize,
    public val borderWidth: Dp,
    public val padding: PaddingValues,
    public val minSize: DpSize,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IconButtonMetrics

        if (cornerSize != other.cornerSize) return false
        if (borderWidth != other.borderWidth) return false
        if (padding != other.padding) return false
        if (minSize != other.minSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cornerSize.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + padding.hashCode()
        result = 31 * result + minSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "IconButtonMetrics(" +
            "cornerSize=$cornerSize, " +
            "borderWidth=$borderWidth, " +
            "padding=$padding, " +
            "minSize=$minSize" +
            ")"
    }

    public companion object
}

public val LocalIconButtonStyle: ProvidableCompositionLocal<IconButtonStyle> = staticCompositionLocalOf {
    error("No IconButtonStyle provided. Have you forgotten the theme?")
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalTransparentIconButtonStyle: ProvidableCompositionLocal<IconButtonStyle> = staticCompositionLocalOf {
    error("No LocalTransparentIconButtonStyle provided. Have you forgotten the theme?")
}
