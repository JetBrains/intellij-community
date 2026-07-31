package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabContentAlpha
import org.jetbrains.jewel.ui.component.styling.TabIcons
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** The factory for creating Int UI default [TabStyle] instances. */
public val TabStyle.Companion.Default: IntUiDefaultTabStyleFactory
    get() = IntUiDefaultTabStyleFactory

/** Factory object for creating Int UI default [TabStyle] instances. */
public object IntUiDefaultTabStyleFactory {
    /** Creates an Int UI light default [TabStyle] with the provided parameters. */
    public fun light(
        colors: TabColors = TabColors.Default.light(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.default(),
        scrollbarStyle: ScrollbarStyle = ScrollbarStyle.tabStripLight(),
    ): TabStyle = TabStyle(colors, metrics, icons, contentAlpha, scrollbarStyle)

    /** Creates an Int UI dark default [TabStyle] with the provided parameters. */
    public fun dark(
        colors: TabColors = TabColors.Default.dark(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.default(),
        scrollbarStyle: ScrollbarStyle = ScrollbarStyle.tabStripDark(),
    ): TabStyle = TabStyle(colors, metrics, icons, contentAlpha, scrollbarStyle)
}

/** The factory for creating Int UI editor [TabStyle] instances. */
public val TabStyle.Companion.Editor: IntUiEditorTabStyleFactory
    get() = IntUiEditorTabStyleFactory

/** Factory object for creating Int UI editor [TabStyle] instances. */
public object IntUiEditorTabStyleFactory {
    /** Creates an Int UI light editor [TabStyle] with the provided parameters. */
    public fun light(
        colors: TabColors = TabColors.Editor.light(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.editor(),
        scrollbarStyle: ScrollbarStyle = ScrollbarStyle.tabStripLight(),
    ): TabStyle = TabStyle(colors, metrics, icons, contentAlpha, scrollbarStyle)

    /** Creates an Int UI dark editor [TabStyle] with the provided parameters. */
    public fun dark(
        colors: TabColors = TabColors.Editor.dark(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.editor(),
        scrollbarStyle: ScrollbarStyle = ScrollbarStyle.tabStripDark(),
    ): TabStyle = TabStyle(colors, metrics, icons, contentAlpha, scrollbarStyle)
}

/** The factory for creating Int UI default [TabColors] instances. */
public val TabColors.Companion.Default: IntUiDefaultTabColorsFactory
    get() = IntUiDefaultTabColorsFactory

/** Factory object for creating Int UI default [TabColors] instances. */
public object IntUiDefaultTabColorsFactory {
    /** Creates an Int UI light default [TabColors] with the provided parameters. */
    public fun light(
        background: Color = IntUiLightTheme.colors.gray(14),
        backgroundHovered: Color = IntUiLightTheme.colors.gray(12),
        backgroundPressed: Color = backgroundHovered,
        backgroundSelected: Color = background,
        backgroundDisabled: Color = background,
        content: Color = IntUiLightTheme.colors.gray(1),
        contentHovered: Color = content,
        contentDisabled: Color = content,
        contentPressed: Color = content,
        contentSelected: Color = content,
        underline: Color = Color.Unspecified,
        underlineHovered: Color = underline,
        underlineDisabled: Color = underline,
        underlinePressed: Color = underline,
        underlineSelected: Color = IntUiLightTheme.colors.blue(4),
    ): TabColors =
        TabColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            backgroundSelected = backgroundSelected,
            content = content,
            contentDisabled = contentDisabled,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            contentSelected = contentSelected,
            underline = underline,
            underlineDisabled = underlineDisabled,
            underlinePressed = underlinePressed,
            underlineHovered = underlineHovered,
            underlineSelected = underlineSelected,
        )

    /** Creates an Int UI dark default [TabColors] with the provided parameters. */
    public fun dark(
        background: Color = Color.Unspecified,
        backgroundHovered: Color = IntUiDarkTheme.colors.gray(4),
        backgroundPressed: Color = backgroundHovered,
        backgroundSelected: Color = background,
        backgroundDisabled: Color = background,
        content: Color = Color.Unspecified,
        contentHovered: Color = content,
        contentDisabled: Color = content,
        contentPressed: Color = content,
        contentSelected: Color = content,
        underline: Color = Color.Unspecified,
        underlineHovered: Color = underline,
        underlineDisabled: Color = underline,
        underlinePressed: Color = underline,
        underlineSelected: Color = IntUiDarkTheme.colors.blue(6),
    ): TabColors =
        TabColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            backgroundSelected = backgroundSelected,
            content = content,
            contentDisabled = contentDisabled,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            contentSelected = contentSelected,
            underline = underline,
            underlineDisabled = underlineDisabled,
            underlinePressed = underlinePressed,
            underlineHovered = underlineHovered,
            underlineSelected = underlineSelected,
        )
}

/** The factory for creating Int UI editor [TabColors] instances. */
public val TabColors.Companion.Editor: IntUiEditorTabColorsFactory
    get() = IntUiEditorTabColorsFactory

/** Factory object for creating Int UI editor [TabColors] instances. */
public object IntUiEditorTabColorsFactory {
    /** Creates an Int UI light editor [TabColors] with the provided parameters. */
    public fun light(
        background: Color = Color.Transparent,
        backgroundHovered: Color = background,
        backgroundPressed: Color = background,
        backgroundSelected: Color = background,
        backgroundDisabled: Color = background,
        content: Color = Color.Unspecified,
        contentHovered: Color = content,
        contentDisabled: Color = content,
        contentPressed: Color = content,
        contentSelected: Color = content,
        underline: Color = Color.Unspecified,
        underlineHovered: Color = underline,
        underlineDisabled: Color = underline,
        underlinePressed: Color = underline,
        underlineSelected: Color = IntUiLightTheme.colors.blue(4),
    ): TabColors =
        TabColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            backgroundSelected = backgroundSelected,
            content = content,
            contentDisabled = contentDisabled,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            contentSelected = contentSelected,
            underline = underline,
            underlineDisabled = underlineDisabled,
            underlinePressed = underlinePressed,
            underlineHovered = underlineHovered,
            underlineSelected = underlineSelected,
        )

    /** Creates an Int UI dark editor [TabColors] with the provided parameters. */
    public fun dark(
        background: Color = Color.Unspecified,
        backgroundHovered: Color = background,
        backgroundPressed: Color = background,
        backgroundSelected: Color = background,
        backgroundDisabled: Color = background,
        content: Color = Color.Unspecified,
        contentHovered: Color = content,
        contentDisabled: Color = content,
        contentPressed: Color = content,
        contentSelected: Color = content,
        underline: Color = Color.Unspecified,
        underlineHovered: Color = underline,
        underlineDisabled: Color = underline,
        underlinePressed: Color = underline,
        underlineSelected: Color = IntUiDarkTheme.colors.blue(6),
    ): TabColors =
        TabColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            backgroundSelected = backgroundSelected,
            content = content,
            contentDisabled = contentDisabled,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
            contentSelected = contentSelected,
            underline = underline,
            underlineDisabled = underlineDisabled,
            underlinePressed = underlinePressed,
            underlineHovered = underlineHovered,
            underlineSelected = underlineSelected,
        )
}

/** Creates an Int UI default [TabMetrics] with the provided parameters. */
public fun TabMetrics.Companion.defaults(
    underlineThickness: Dp = 3.dp,
    tabPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    closeContentGap: Dp = 8.dp,
    tabContentSpacing: Dp = 4.dp,
    tabHeight: Dp = 40.dp,
): TabMetrics = TabMetrics(underlineThickness, tabPadding, tabHeight, tabContentSpacing, closeContentGap)

/** Creates an Int UI default [TabContentAlpha] with the provided parameters. */
public fun TabContentAlpha.Companion.default(
    iconNormal: Float = 1f,
    iconDisabled: Float = iconNormal,
    iconPressed: Float = iconNormal,
    iconHovered: Float = iconNormal,
    iconSelected: Float = iconNormal,
    contentNormal: Float = iconNormal,
    contentDisabled: Float = iconNormal,
    contentPressed: Float = iconNormal,
    contentHovered: Float = iconNormal,
    contentSelected: Float = iconNormal,
): TabContentAlpha =
    TabContentAlpha(
        iconNormal = iconNormal,
        iconDisabled = iconDisabled,
        iconPressed = iconPressed,
        iconHovered = iconHovered,
        iconSelected = iconSelected,
        contentNormal = contentNormal,
        contentDisabled = contentDisabled,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        contentSelected = contentSelected,
    )

/** Creates an Int UI editor [TabContentAlpha] with the provided parameters. */
public fun TabContentAlpha.Companion.editor(
    iconNormal: Float = .7f,
    iconDisabled: Float = iconNormal,
    iconPressed: Float = 1f,
    iconHovered: Float = iconPressed,
    iconSelected: Float = iconPressed,
    contentNormal: Float = .9f,
    contentDisabled: Float = contentNormal,
    contentPressed: Float = 1f,
    contentHovered: Float = contentPressed,
    contentSelected: Float = contentPressed,
): TabContentAlpha =
    TabContentAlpha(
        iconNormal = iconNormal,
        iconDisabled = iconDisabled,
        iconPressed = iconPressed,
        iconHovered = iconHovered,
        iconSelected = iconSelected,
        contentNormal = contentNormal,
        contentDisabled = contentDisabled,
        contentPressed = contentPressed,
        contentHovered = contentHovered,
        contentSelected = contentSelected,
    )

/** Creates an Int UI default [TabIcons] with the provided parameters. */
public fun TabIcons.Companion.defaults(close: IconKey = AllIconsKeys.General.CloseSmall): TabIcons = TabIcons(close)
