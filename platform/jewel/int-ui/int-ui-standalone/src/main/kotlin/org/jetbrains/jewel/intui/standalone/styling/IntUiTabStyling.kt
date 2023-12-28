package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.standalonePainterProvider
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabContentAlpha
import org.jetbrains.jewel.ui.component.styling.TabIcons
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.painter.PainterProvider

public val TabStyle.Companion.Default: IntUiDefaultTabStyleFactory
    get() = IntUiDefaultTabStyleFactory

public object IntUiDefaultTabStyleFactory {

    @Composable
    public fun light(
        colors: TabColors = TabColors.Default.light(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.default(),
    ): TabStyle =
        TabStyle(colors, metrics, icons, contentAlpha)

    @Composable
    public fun dark(
        colors: TabColors = TabColors.Default.dark(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.default(),
    ): TabStyle =
        TabStyle(colors, metrics, icons, contentAlpha)
}

public val TabStyle.Companion.Editor: IntUiEditorTabStyleFactory
    get() = IntUiEditorTabStyleFactory

public object IntUiEditorTabStyleFactory {

    @Composable
    public fun light(
        colors: TabColors = TabColors.Editor.light(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.editor(),
    ): TabStyle =
        TabStyle(colors, metrics, icons, contentAlpha)

    @Composable
    public fun dark(
        colors: TabColors = TabColors.Editor.dark(),
        metrics: TabMetrics = TabMetrics.defaults(),
        icons: TabIcons = TabIcons.defaults(),
        contentAlpha: TabContentAlpha = TabContentAlpha.editor(),
    ): TabStyle =
        TabStyle(colors, metrics, icons, contentAlpha)
}

public val TabColors.Companion.Default: IntUiDefaultTabColorsFactory
    get() = IntUiDefaultTabColorsFactory

public object IntUiDefaultTabColorsFactory {

    public fun light(
        background: Color = IntUiLightTheme.colors.grey(14),
        backgroundHovered: Color = IntUiLightTheme.colors.grey(12),
        backgroundPressed: Color = backgroundHovered,
        backgroundSelected: Color = background,
        backgroundDisabled: Color = background,
        content: Color = IntUiLightTheme.colors.grey(1),
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

    public fun dark(
        background: Color = Color.Unspecified,
        backgroundHovered: Color = IntUiDarkTheme.colors.grey(4),
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

public val TabColors.Companion.Editor: IntUiEditorTabColorsFactory
    get() = IntUiEditorTabColorsFactory

public object IntUiEditorTabColorsFactory {

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

public fun TabMetrics.Companion.defaults(
    underlineThickness: Dp = 3.dp,
    tabPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
    closeContentGap: Dp = 8.dp,
    tabContentSpacing: Dp = 4.dp,
    tabHeight: Dp = 40.dp,
): TabMetrics =
    TabMetrics(underlineThickness, tabPadding, tabHeight, tabContentSpacing, closeContentGap)

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

public fun TabIcons.Companion.defaults(
    close: PainterProvider = standalonePainterProvider("expui/general/closeSmall.svg"),
): TabIcons =
    TabIcons(close)
