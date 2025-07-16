package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle

public fun IconButtonStyle.Companion.light(
    colors: IconButtonColors = IconButtonColors.light(),
    metrics: IconButtonMetrics = IconButtonMetrics.defaults(),
): IconButtonStyle = IconButtonStyle(colors, metrics)

public fun IconButtonStyle.Companion.dark(
    colors: IconButtonColors = IconButtonColors.dark(),
    metrics: IconButtonMetrics = IconButtonMetrics.defaults(),
): IconButtonStyle = IconButtonStyle(colors, metrics)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun IconButtonStyle.Companion.lightTransparentBackground(
    colors: IconButtonColors = IconButtonColors.lightTransparentBackground(),
    metrics: IconButtonMetrics = IconButtonMetrics.defaults(),
): IconButtonStyle = IconButtonStyle(colors, metrics)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun IconButtonStyle.Companion.darkTransparentBackground(
    colors: IconButtonColors = IconButtonColors.darkTransparentBackground(),
    metrics: IconButtonMetrics = IconButtonMetrics.defaults(),
): IconButtonStyle = IconButtonStyle(colors, metrics)

public fun IconButtonColors.Companion.light(
    foregroundSelectedActivated: Color = IntUiLightTheme.colors.gray(14),
    background: Color = Color.Unspecified,
    backgroundDisabled: Color = background,
    backgroundSelected: Color = IntUiLightTheme.colors.gray(11),
    backgroundSelectedActivated: Color = IntUiLightTheme.colors.blue(4),
    backgroundPressed: Color = IntUiLightTheme.colors.gray(11),
    backgroundHovered: Color = IntUiLightTheme.colors.gray(12),
    backgroundFocused: Color = backgroundHovered,
    border: Color = background,
    borderDisabled: Color = backgroundDisabled,
    borderSelected: Color = backgroundSelected,
    borderSelectedActivated: Color = backgroundSelectedActivated,
    borderFocused: Color = backgroundFocused,
    borderPressed: Color = backgroundPressed,
    borderHovered: Color = backgroundHovered,
): IconButtonColors =
    IconButtonColors(
        foregroundSelectedActivated = foregroundSelectedActivated,
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundSelected = backgroundSelected,
        backgroundSelectedActivated = backgroundSelectedActivated,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderSelected = borderSelected,
        borderSelectedActivated = borderSelectedActivated,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun IconButtonColors.Companion.lightTransparentBackground(
    foregroundSelectedActivated: Color = IntUiLightTheme.colors.gray(14)
): IconButtonColors =
    IconButtonColors(
        foregroundSelectedActivated = foregroundSelectedActivated,
        border = Color.Transparent,
        borderDisabled = Color.Transparent,
        borderSelected = Color.Transparent,
        borderSelectedActivated = Color.Transparent,
        background = Color.Transparent,
        backgroundDisabled = Color.Transparent,
        backgroundSelected = Color.Transparent,
        backgroundSelectedActivated = Color.Transparent,
        backgroundFocused = Color.Transparent,
        backgroundPressed = Color.Transparent,
        backgroundHovered = Color.Transparent,
        borderFocused = Color.Transparent,
        borderPressed = Color.Transparent,
        borderHovered = Color.Transparent,
    )

public fun IconButtonColors.Companion.dark(
    foregroundSelectedActivated: Color = IntUiDarkTheme.colors.gray(14),
    background: Color = Color.Unspecified,
    backgroundDisabled: Color = background,
    backgroundSelected: Color = IntUiDarkTheme.colors.gray(5),
    backgroundSelectedActivated: Color = IntUiDarkTheme.colors.blue(6),
    backgroundPressed: Color = IntUiDarkTheme.colors.gray(5),
    backgroundHovered: Color = IntUiDarkTheme.colors.gray(3),
    backgroundFocused: Color = backgroundHovered,
    border: Color = background,
    borderDisabled: Color = backgroundDisabled,
    borderSelected: Color = backgroundSelected,
    borderSelectedActivated: Color = backgroundSelectedActivated,
    borderFocused: Color = backgroundFocused,
    borderPressed: Color = backgroundPressed,
    borderHovered: Color = backgroundHovered,
): IconButtonColors =
    IconButtonColors(
        foregroundSelectedActivated = foregroundSelectedActivated,
        background = background,
        backgroundDisabled = backgroundDisabled,
        backgroundSelected = backgroundSelected,
        backgroundSelectedActivated = backgroundSelectedActivated,
        backgroundFocused = backgroundFocused,
        backgroundPressed = backgroundPressed,
        backgroundHovered = backgroundHovered,
        border = border,
        borderDisabled = borderDisabled,
        borderSelected = borderSelected,
        borderSelectedActivated = borderSelectedActivated,
        borderFocused = borderFocused,
        borderPressed = borderPressed,
        borderHovered = borderHovered,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun IconButtonColors.Companion.darkTransparentBackground(
    foregroundSelectedActivated: Color = IntUiDarkTheme.colors.gray(14)
): IconButtonColors =
    IconButtonColors(
        foregroundSelectedActivated = foregroundSelectedActivated,
        background = Color.Transparent,
        backgroundDisabled = Color.Transparent,
        backgroundSelected = Color.Transparent,
        backgroundSelectedActivated = Color.Transparent,
        backgroundFocused = Color.Transparent,
        backgroundPressed = Color.Transparent,
        backgroundHovered = Color.Transparent,
        border = Color.Transparent,
        borderDisabled = Color.Transparent,
        borderSelected = Color.Transparent,
        borderSelectedActivated = Color.Transparent,
        borderFocused = Color.Transparent,
        borderPressed = Color.Transparent,
        borderHovered = Color.Transparent,
    )

public fun IconButtonMetrics.Companion.defaults(
    cornerSize: CornerSize = CornerSize(4.dp),
    borderWidth: Dp = 1.dp,
    padding: PaddingValues = PaddingValues(0.dp),
    minSize: DpSize = DpSize(24.dp, 24.dp),
): IconButtonMetrics = IconButtonMetrics(cornerSize, borderWidth, padding, minSize)
