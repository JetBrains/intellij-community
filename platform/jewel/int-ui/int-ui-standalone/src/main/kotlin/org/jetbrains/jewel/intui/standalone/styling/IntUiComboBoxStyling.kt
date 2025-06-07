package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.ComboBoxColors
import org.jetbrains.jewel.ui.component.styling.ComboBoxIcons
import org.jetbrains.jewel.ui.component.styling.ComboBoxMetrics
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

public val ComboBoxStyle.Companion.Default: IntUiDefaultComboBoxStyleFactory
    get() = IntUiDefaultComboBoxStyleFactory

public object IntUiDefaultComboBoxStyleFactory {
    public fun light(
        colors: ComboBoxColors = ComboBoxColors.Default.light(),
        metrics: ComboBoxMetrics = ComboBoxMetrics.default(),
        icons: ComboBoxIcons = ComboBoxIcons.defaults(),
    ): ComboBoxStyle = ComboBoxStyle(colors, metrics, icons)

    public fun dark(
        colors: ComboBoxColors = ComboBoxColors.Default.dark(),
        metrics: ComboBoxMetrics = ComboBoxMetrics.default(),
        icons: ComboBoxIcons = ComboBoxIcons.defaults(),
    ): ComboBoxStyle = ComboBoxStyle(colors, metrics, icons)
}

public val ComboBoxStyle.Companion.Undecorated: IntUiUndecoratedComboBoxStyleFactory
    get() = IntUiUndecoratedComboBoxStyleFactory

public object IntUiUndecoratedComboBoxStyleFactory {
    public fun light(
        colors: ComboBoxColors = ComboBoxColors.Undecorated.light(),
        metrics: ComboBoxMetrics = ComboBoxMetrics.undecorated(),
        icons: ComboBoxIcons = ComboBoxIcons.defaults(),
    ): ComboBoxStyle = ComboBoxStyle(colors, metrics, icons)

    public fun dark(
        colors: ComboBoxColors = ComboBoxColors.Undecorated.dark(),
        metrics: ComboBoxMetrics = ComboBoxMetrics.undecorated(),
        icons: ComboBoxIcons = ComboBoxIcons.defaults(),
    ): ComboBoxStyle = ComboBoxStyle(colors, metrics, icons)
}

public val ComboBoxColors.Companion.Default: IntUiDefaultComboBoxColorsFactory
    get() = IntUiDefaultComboBoxColorsFactory

public object IntUiDefaultComboBoxColorsFactory {
    public fun light(
        background: Color = IntUiLightTheme.colors.gray(14),
        backgroundDisabled: Color = IntUiLightTheme.colors.gray(13),
        backgroundFocused: Color = background,
        backgroundPressed: Color = background,
        backgroundHovered: Color = background,
        nonEditableBackground: Color = White,
        content: Color = IntUiLightTheme.colors.gray(1),
        contentDisabled: Color = IntUiLightTheme.colors.gray(8),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        border: Color = IntUiLightTheme.colors.gray(9),
        borderDisabled: Color = IntUiLightTheme.colors.gray(11),
        borderFocused: Color = IntUiLightTheme.colors.blue(4),
        borderPressed: Color = border,
        borderHovered: Color = border,
    ): ComboBoxColors =
        ComboBoxColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            nonEditableBackground = nonEditableBackground,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            border = border,
            borderDisabled = borderDisabled,
            borderFocused = borderFocused,
            borderPressed = borderPressed,
            borderHovered = borderHovered,
        )

    public fun dark(
        background: Color = IntUiDarkTheme.colors.gray(2),
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = background,
        backgroundHovered: Color = background,
        nonEditableBackground: Color = IntUiDarkTheme.colors.gray(3),
        content: Color = IntUiDarkTheme.colors.gray(12),
        contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        border: Color = IntUiDarkTheme.colors.gray(5),
        borderDisabled: Color = IntUiDarkTheme.colors.gray(5),
        borderFocused: Color = IntUiDarkTheme.colors.blue(6),
        borderPressed: Color = border,
        borderHovered: Color = border,
    ): ComboBoxColors =
        ComboBoxColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            nonEditableBackground = nonEditableBackground,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            border = border,
            borderDisabled = borderDisabled,
            borderFocused = borderFocused,
            borderPressed = borderPressed,
            borderHovered = borderHovered,
        )
}

public val ComboBoxColors.Companion.Undecorated: IntUiUndecoratedComboBoxColorsFactory
    get() = IntUiUndecoratedComboBoxColorsFactory

public object IntUiUndecoratedComboBoxColorsFactory {
    public fun light(
        background: Color = Color.Transparent,
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = IntUiLightTheme.colors.gray(14).copy(alpha = 0.1f),
        backgroundHovered: Color = backgroundPressed,
        nonEditableBackground: Color = White,
        content: Color = IntUiLightTheme.colors.gray(1),
        contentDisabled: Color = IntUiLightTheme.colors.gray(8),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
    ): ComboBoxColors =
        ComboBoxColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            nonEditableBackground = nonEditableBackground,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            border = Color.Transparent,
            borderDisabled = Color.Transparent,
            borderFocused = Color.Transparent,
            borderPressed = Color.Transparent,
            borderHovered = Color.Transparent,
        )

    public fun dark(
        background: Color = Color.Transparent,
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = Color(0x0D000000), // Not a palette color
        backgroundHovered: Color = backgroundPressed,
        nonEditableBackground: Color = IntUiDarkTheme.colors.gray(3),
        content: Color = IntUiDarkTheme.colors.gray(12),
        contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
    ): ComboBoxColors =
        ComboBoxColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            nonEditableBackground = nonEditableBackground,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            border = Color.Transparent,
            borderDisabled = Color.Transparent,
            borderFocused = Color.Transparent,
            borderPressed = Color.Transparent,
            borderHovered = Color.Transparent,
        )
}

public fun ComboBoxMetrics.Companion.default(
    arrowAreaSize: DpSize = DpSize(28.dp, 28.dp),
    minSize: DpSize = DpSize(77.dp, 28.dp),
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(9.dp, 1.dp, 6.dp, 1.dp),
    popupContentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    borderWidth: Dp = 1.dp,
    maxPopupHeight: Dp = 200.dp,
): ComboBoxMetrics =
    ComboBoxMetrics(
        arrowAreaSize,
        minSize,
        cornerSize,
        contentPadding,
        popupContentPadding,
        borderWidth,
        maxPopupHeight,
    )

public fun ComboBoxMetrics.Companion.undecorated(
    arrowAreaSize: DpSize = DpSize(28.dp, 28.dp),
    minSize: DpSize = DpSize(77.dp, 28.dp),
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(9.dp, 1.dp, 6.dp, 1.dp),
    popupContentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    borderWidth: Dp = 0.dp,
    maxPopupHeight: Dp = 200.dp,
): ComboBoxMetrics =
    ComboBoxMetrics(
        arrowAreaSize,
        minSize,
        cornerSize,
        contentPadding,
        popupContentPadding,
        borderWidth,
        maxPopupHeight,
    )

public fun ComboBoxIcons.Companion.defaults(chevronDown: IconKey = AllIconsKeys.General.ChevronDown): ComboBoxIcons =
    ComboBoxIcons(chevronDown)
