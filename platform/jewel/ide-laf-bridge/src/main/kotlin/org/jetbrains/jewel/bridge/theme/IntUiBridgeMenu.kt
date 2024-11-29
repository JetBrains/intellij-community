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
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
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

    val colors =
        MenuColors(
            background = retrieveColorOrUnspecified("PopupMenu.background"),
            border =
                retrieveColorOrUnspecified("Popup.borderColor").takeOrElse {
                    retrieveColorOrUnspecified("Popup.Border.color")
                },
            shadow = Color.Black.copy(alpha = .6f),
            itemColors =
                MenuItemColors(
                    background = retrieveColorOrUnspecified("MenuItem.background"),
                    backgroundDisabled = retrieveColorOrUnspecified("MenuItem.disabledBackground"),
                    backgroundFocused = backgroundSelected,
                    backgroundPressed = backgroundSelected,
                    backgroundHovered = backgroundSelected,
                    content = retrieveColorOrUnspecified("PopupMenu.foreground"),
                    contentDisabled = retrieveColorOrUnspecified("PopupMenu.disabledForeground"),
                    contentFocused = foregroundSelected,
                    contentPressed = foregroundSelected,
                    contentHovered = foregroundSelected,
                    iconTint = Color.Unspecified,
                    iconTintDisabled = Color.Unspecified,
                    iconTintFocused = Color.Unspecified,
                    iconTintPressed = Color.Unspecified,
                    iconTintHovered = Color.Unspecified,
                    keybindingTint = keybindingTint,
                    keybindingTintDisabled = keybindingTint,
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
                cornerSize = CornerSize(IdeaPopupMenuUI.CORNER_RADIUS.dp),
                menuMargin = PaddingValues(),
                contentPadding = PaddingValues(),
                offset = DpOffset(0.dp, 2.dp),
                shadowSize = 12.dp,
                borderWidth = retrieveIntAsDpOrUnspecified("Popup.borderWidth").takeOrElse { 1.dp },
                itemMetrics =
                    MenuItemMetrics(
                        selectionCornerSize = CornerSize(0.dp),
                        outerPadding = PaddingValues(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        keybindingsPadding = PaddingValues(start = 36.dp),
                        separatorPadding =
                            PaddingValues(
                                horizontal =
                                    retrieveIntAsDpOrUnspecified("PopupMenuSeparator.withToEdge").takeOrElse { 1.dp },
                                vertical =
                                    retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeIndent").takeOrElse { 1.dp },
                            ),
                        separatorThickness =
                            retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeWidth").takeOrElse { 1.dp },
                        separatorHeight = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.height").takeOrElse { 3.dp },
                        iconSize = 16.dp,
                        minHeight = if (isNewUiTheme()) JBUI.CurrentTheme.List.rowHeight().dp else Dp.Unspecified,
                    ),
                submenuMetrics = SubmenuMetrics(offset = DpOffset(0.dp, (-8).dp)),
            ),
        icons = MenuIcons(submenuChevron = AllIconsKeys.General.ChevronRight),
    )
}
