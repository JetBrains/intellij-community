package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabContentAlpha
import org.jetbrains.jewel.ui.component.styling.TabIcons
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

// See com.intellij.ui.tabs.impl.themes.DefaultTabTheme
internal fun readDefaultTabStyle(): TabStyle {
    val normalBackground = JBUI.CurrentTheme.DefaultTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.DefaultTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors =
        TabColors(
            background = normalBackground,
            backgroundDisabled = normalBackground,
            backgroundPressed = selectedBackground,
            backgroundHovered = JBUI.CurrentTheme.DefaultTabs.hoverBackground().toComposeColor(),
            backgroundSelected = selectedBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("TabbedPane.disabledForeground"),
            contentPressed = normalContent,
            contentHovered = normalContent,
            contentSelected = normalContent,
            underline = Color.Unspecified,
            underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
            underlinePressed = selectedUnderline,
            underlineHovered = Color.Unspecified,
            underlineSelected = selectedUnderline,
        )

    return TabStyle(
        colors = colors,
        metrics =
            TabMetrics(
                underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight").takeOrElse { 2.dp },
                tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
                closeContentGap = 4.dp,
                tabContentSpacing = 4.dp,
                tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
            ),
        icons = TabIcons(close = AllIconsKeys.General.CloseSmall),
        contentAlpha =
            TabContentAlpha(
                iconNormal = 1f,
                iconDisabled = 1f,
                iconPressed = 1f,
                iconHovered = 1f,
                iconSelected = 1f,
                contentNormal = 1f,
                contentDisabled = 1f,
                contentPressed = 1f,
                contentHovered = 1f,
                contentSelected = 1f,
            ),
        scrollbarStyle = readScrollbarStyle(isDark),
    )
}

internal fun readEditorTabStyle(): TabStyle {
    val normalBackground = JBUI.CurrentTheme.EditorTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.EditorTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors =
        TabColors(
            background = normalBackground,
            backgroundDisabled = normalBackground,
            backgroundPressed = selectedBackground,
            backgroundHovered = JBUI.CurrentTheme.EditorTabs.hoverBackground().toComposeColor(),
            backgroundSelected = selectedBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("TabbedPane.disabledForeground"),
            contentPressed = normalContent,
            contentHovered = normalContent,
            contentSelected = normalContent,
            underline = Color.Unspecified,
            underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
            underlinePressed = selectedUnderline,
            underlineHovered = Color.Unspecified,
            underlineSelected = selectedUnderline,
        )

    return TabStyle(
        colors = colors,
        metrics =
            TabMetrics(
                underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight").takeOrElse { 2.dp },
                tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
                closeContentGap = 4.dp,
                tabContentSpacing = 4.dp,
                tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
            ),
        icons = TabIcons(close = AllIconsKeys.General.CloseSmall),
        contentAlpha =
            TabContentAlpha(
                iconNormal = .7f,
                iconDisabled = .7f,
                iconPressed = 1f,
                iconHovered = 1f,
                iconSelected = 1f,
                contentNormal = .7f,
                contentDisabled = .7f,
                contentPressed = 1f,
                contentHovered = 1f,
                contentSelected = 1f,
            ),
        scrollbarStyle = readScrollbarStyle(isDark),
    )
}
