package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownIcons
import org.jetbrains.jewel.ui.component.styling.DropdownMetrics
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** Provides access to the factory for creating Int UI default [DropdownStyle] instances. */
public val DropdownStyle.Companion.Default: IntUiDefaultDropdownStyleFactory
    get() = IntUiDefaultDropdownStyleFactory

/** Factory for creating Int UI default [DropdownStyle] instances. */
public object IntUiDefaultDropdownStyleFactory {
    /** Creates an Int UI light default [DropdownStyle] with the provided parameters. */
    public fun light(
        colors: DropdownColors = DropdownColors.Default.light(),
        metrics: DropdownMetrics = DropdownMetrics.default(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        menuStyle: MenuStyle = MenuStyle.light(),
    ): DropdownStyle = DropdownStyle(colors, metrics, icons, menuStyle)

    /** Creates an Int UI dark default [DropdownStyle] with the provided parameters. */
    public fun dark(
        colors: DropdownColors = DropdownColors.Default.dark(),
        metrics: DropdownMetrics = DropdownMetrics.default(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        menuStyle: MenuStyle = MenuStyle.dark(),
    ): DropdownStyle = DropdownStyle(colors, metrics, icons, menuStyle)
}

/** Provides access to the factory for creating Int UI undecorated [DropdownStyle] instances. */
public val DropdownStyle.Companion.Undecorated: IntUiUndecoratedDropdownStyleFactory
    get() = IntUiUndecoratedDropdownStyleFactory

/** Factory for creating Int UI undecorated [DropdownStyle] instances. */
public object IntUiUndecoratedDropdownStyleFactory {
    /** Creates an Int UI light undecorated [DropdownStyle] with the provided parameters. */
    public fun light(
        colors: DropdownColors = DropdownColors.Undecorated.light(),
        metrics: DropdownMetrics = DropdownMetrics.undecorated(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        menuStyle: MenuStyle = MenuStyle.light(),
    ): DropdownStyle = DropdownStyle(colors, metrics, icons, menuStyle)

    /** Creates an Int UI dark undecorated [DropdownStyle] with the provided parameters. */
    public fun dark(
        colors: DropdownColors = DropdownColors.Undecorated.dark(),
        metrics: DropdownMetrics = DropdownMetrics.undecorated(),
        icons: DropdownIcons = DropdownIcons.defaults(),
        menuStyle: MenuStyle = MenuStyle.dark(),
    ): DropdownStyle = DropdownStyle(colors, metrics, icons, menuStyle)
}

/** Provides access to the factory for creating Int UI default [DropdownColors] instances. */
public val DropdownColors.Companion.Default: IntUiDefaultDropdownColorsFactory
    get() = IntUiDefaultDropdownColorsFactory

/** Factory for creating Int UI default [DropdownColors] instances. */
public object IntUiDefaultDropdownColorsFactory {
    /** Creates an Int UI light default [DropdownColors] with the provided parameters. */
    public fun light(
        background: Color = IntUiLightTheme.colors.gray(14),
        backgroundDisabled: Color = IntUiLightTheme.colors.gray(13),
        backgroundFocused: Color = background,
        backgroundPressed: Color = background,
        backgroundHovered: Color = background,
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
        iconTint: Color = IntUiLightTheme.colors.gray(7),
        iconTintDisabled: Color = IntUiLightTheme.colors.gray(9),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ): DropdownColors =
        DropdownColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
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
            iconTint = iconTint,
            iconTintDisabled = iconTintDisabled,
            iconTintFocused = iconTintFocused,
            iconTintPressed = iconTintPressed,
            iconTintHovered = iconTintHovered,
        )

    /** Creates an Int UI dark default [DropdownColors] with the provided parameters. */
    public fun dark(
        background: Color = IntUiDarkTheme.colors.gray(2),
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = background,
        backgroundHovered: Color = background,
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
        iconTint: Color = IntUiDarkTheme.colors.gray(10),
        iconTintDisabled: Color = IntUiDarkTheme.colors.gray(6),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ): DropdownColors =
        DropdownColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
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
            iconTint = iconTint,
            iconTintDisabled = iconTintDisabled,
            iconTintFocused = iconTintFocused,
            iconTintPressed = iconTintPressed,
            iconTintHovered = iconTintHovered,
        )
}

/** Provides access to the factory for creating Int UI undecorated [DropdownColors] instances. */
public val DropdownColors.Companion.Undecorated: IntUiUndecoratedDropdownColorsFactory
    get() = IntUiUndecoratedDropdownColorsFactory

/** Factory for creating Int UI undecorated [DropdownColors] instances. */
public object IntUiUndecoratedDropdownColorsFactory {
    /** Creates an Int UI light undecorated [DropdownColors] with the provided parameters. */
    public fun light(
        background: Color = Color.Transparent,
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = IntUiLightTheme.colors.gray(14).copy(alpha = 0.1f),
        backgroundHovered: Color = backgroundPressed,
        content: Color = IntUiLightTheme.colors.gray(1),
        contentDisabled: Color = IntUiLightTheme.colors.gray(8),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        iconTint: Color = IntUiLightTheme.colors.gray(7),
        iconTintDisabled: Color = IntUiLightTheme.colors.gray(9),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ): DropdownColors =
        DropdownColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
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
            iconTint = iconTint,
            iconTintDisabled = iconTintDisabled,
            iconTintFocused = iconTintFocused,
            iconTintPressed = iconTintPressed,
            iconTintHovered = iconTintHovered,
        )

    /** Creates an Int UI dark undecorated [DropdownColors] with the provided parameters. */
    public fun dark(
        background: Color = Color.Transparent,
        backgroundDisabled: Color = background,
        backgroundFocused: Color = background,
        backgroundPressed: Color = Color(0x0D000000), // Not a palette color
        backgroundHovered: Color = backgroundPressed,
        content: Color = IntUiDarkTheme.colors.gray(12),
        contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
        contentFocused: Color = content,
        contentPressed: Color = content,
        contentHovered: Color = content,
        iconTint: Color = IntUiDarkTheme.colors.gray(10),
        iconTintDisabled: Color = IntUiDarkTheme.colors.gray(6),
        iconTintFocused: Color = iconTint,
        iconTintPressed: Color = iconTint,
        iconTintHovered: Color = iconTint,
    ): DropdownColors =
        DropdownColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
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
            iconTint = iconTint,
            iconTintDisabled = iconTintDisabled,
            iconTintFocused = iconTintFocused,
            iconTintPressed = iconTintPressed,
            iconTintHovered = iconTintHovered,
        )
}

/** Creates an Int UI default [DropdownMetrics] with the provided parameters. */
public fun DropdownMetrics.Companion.default(
    arrowMinSize: DpSize = DpSize((23 + 3).dp, 24.dp),
    minSize: DpSize = DpSize((49 + 23 + 6).dp, 24.dp),
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
    borderWidth: Dp = 1.dp,
): DropdownMetrics = DropdownMetrics(arrowMinSize, minSize, cornerSize, contentPadding, borderWidth)

/** Creates an Int UI undecorated [DropdownMetrics] with the provided parameters. */
public fun DropdownMetrics.Companion.undecorated(
    arrowMinSize: DpSize = DpSize((23 + 3).dp, 24.dp),
    minSize: DpSize = DpSize((49 + 23 + 6).dp, 24.dp),
    cornerSize: CornerSize = CornerSize(4.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
    borderWidth: Dp = 0.dp,
): DropdownMetrics = DropdownMetrics(arrowMinSize, minSize, cornerSize, contentPadding, borderWidth)

/** Creates an Int UI default [DropdownIcons] with the provided parameters. */
public fun DropdownIcons.Companion.defaults(chevronDown: IconKey = AllIconsKeys.General.ChevronDown): DropdownIcons =
    DropdownIcons(chevronDown)
