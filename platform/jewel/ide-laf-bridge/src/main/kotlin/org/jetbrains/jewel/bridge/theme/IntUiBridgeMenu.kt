package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.isNewUiTheme
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveIntAsNonNegativeDpOrUnspecified
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuIcons
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuItemMetrics
import org.jetbrains.jewel.ui.component.styling.MenuMetrics
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.SubmenuMetrics
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readMenuStyle(): MenuStyle {
    val backgroundSelected = retrieveColorOrUnspecified("MenuItem.selectionBackground")
    val foregroundSelected = retrieveColorOrUnspecified("MenuItem.selectionForeground")
    val keybindingTint = retrieveColorOrUnspecified("MenuItem.acceleratorForeground")
    val keybindingTintSelected = Color.Unspecified

    val contentDisabled = retrieveColorOrUnspecified("PopupMenu.disabledForeground")

    val colors =
        MenuColors(
            background = retrieveColorOrUnspecified("PopupMenu.background"),
            border =
                retrieveColorOrUnspecified("Popup.borderColor").takeOrElse {
                    retrieveColorOrUnspecified("Popup.Border.color")
                },
            shadow =
                if (isDark) {
                    Color(0x66000000)
                } else {
                    Color(0x78919191)
                },
            itemColors =
                MenuItemColors(
                    background = retrieveColorOrUnspecified("MenuItem.background"),
                    backgroundDisabled = retrieveColorOrUnspecified("MenuItem.disabledBackground"),
                    backgroundFocused = backgroundSelected,
                    backgroundPressed = backgroundSelected,
                    backgroundHovered = backgroundSelected,
                    content = retrieveColorOrUnspecified("PopupMenu.foreground"),
                    contentDisabled = contentDisabled,
                    contentFocused = foregroundSelected,
                    contentPressed = foregroundSelected,
                    contentHovered = foregroundSelected,
                    iconTint = Color.Unspecified,
                    iconTintDisabled = Color.Unspecified,
                    iconTintFocused = Color.Unspecified,
                    iconTintPressed = Color.Unspecified,
                    iconTintHovered = Color.Unspecified,
                    keybindingTint = keybindingTint,
                    keybindingTintDisabled = contentDisabled,
                    keybindingTintFocused = keybindingTintSelected,
                    keybindingTintPressed = keybindingTintSelected,
                    keybindingTintHovered = keybindingTintSelected,
                    separator = retrieveColorOrUnspecified("Menu.separatorColor"),
                ),
        )

    return MenuStyle(
        isDark = isDark,
        colors = colors,
        metrics =
            MenuMetrics(
                cornerSize = CornerSize(IdeaPopupMenuUI.CORNER_RADIUS.dp.safeValue()),
                menuMargin = PaddingValues(vertical = 6.dp),
                contentPadding = PaddingValues(vertical = 7.dp, horizontal = 2.dp),
                offset = DpOffset(0.dp, 2.dp),
                shadowSize = 12.dp,
                borderWidth = retrieveIntAsNonNegativeDpOrUnspecified("Popup.borderWidth").takeOrElse { 1.dp },
                itemMetrics =
                    MenuItemMetrics(
                        selectionCornerSize = CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp.safeValue() / 2),
                        outerPadding = JBUI.CurrentTheme.PopupMenu.Selection.outerInsets().toPaddingValues(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        keybindingsPadding = PaddingValues(start = 36.dp),
                        separatorPadding =
                            PaddingValues(
                                horizontal =
                                    retrieveIntAsNonNegativeDpOrUnspecified("PopupMenuSeparator.withToEdge")
                                        .takeOrElse { 1.dp },
                                vertical =
                                    retrieveIntAsNonNegativeDpOrUnspecified("PopupMenuSeparator.stripeIndent")
                                        .takeOrElse { 1.dp },
                            ),
                        separatorThickness =
                            retrieveIntAsNonNegativeDpOrUnspecified("PopupMenuSeparator.stripeWidth").takeOrElse {
                                1.dp
                            },
                        separatorHeight =
                            retrieveIntAsNonNegativeDpOrUnspecified("PopupMenuSeparator.height").takeOrElse { 3.dp },
                        iconSize = 16.dp,
                        minHeight =
                            if (isNewUiTheme()) {
                                JBUI.CurrentTheme.List.rowHeight().dp.safeValue()
                            } else {
                                Dp.Unspecified
                            },
                    ),
                submenuMetrics = SubmenuMetrics(offset = DpOffset(0.dp, (-8).dp)),
            ),
        icons = MenuIcons(submenuChevron = AllIconsKeys.General.ChevronRight),
    )
}
