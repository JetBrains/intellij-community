package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toNonNegativeDpSize
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownIcons
import org.jetbrains.jewel.ui.component.styling.DropdownMetrics
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readDefaultDropdownStyle(menuStyle: MenuStyle): DropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.background")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")
    val normalBorder = retrieveColorOrUnspecified("Component.borderColor")
    val focusedBorder = retrieveColorOrUnspecified("Component.focusedBorderColor")

    val colors =
        DropdownColors(
            background = normalBackground,
            backgroundDisabled = retrieveColorOrUnspecified("ComboBox.disabledBackground"),
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("ComboBox.disabledForeground"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = retrieveColorOrUnspecified("Component.disabledBorderColor"),
            borderFocused = focusedBorder,
            borderPressed = focusedBorder,
            borderHovered = normalBorder,
            iconTint = Color.Unspecified,
            iconTintDisabled = Color.Unspecified,
            iconTintFocused = Color.Unspecified,
            iconTintPressed = Color.Unspecified,
            iconTintHovered = Color.Unspecified,
        )

    val minimumSize = JBUI.CurrentTheme.ComboBox.minimumSize().toNonNegativeDpSize()
    val arrowWidth = JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH.dp.safeValue()
    return DropdownStyle(
        colors = colors,
        metrics =
            DropdownMetrics(
                arrowMinSize = DpSize(arrowWidth, minimumSize.height),
                minSize = DpSize(minimumSize.width + arrowWidth, minimumSize.height),
                cornerSize = componentArc,
                contentPadding =
                    retrieveInsetsAsPaddingValues(
                        "ComboBox.padding",
                        PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                    ),
                borderWidth = borderWidth,
            ),
        icons = DropdownIcons(chevronDown = AllIconsKeys.General.ChevronDown),
        menuStyle = menuStyle,
    )
}

internal fun readUndecoratedDropdownStyle(menuStyle: MenuStyle): DropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.background")
    val hoverBackground = retrieveColorOrUnspecified("MainToolbar.Dropdown.transparentHoverBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")

    val colors =
        DropdownColors(
            background = normalBackground,
            backgroundDisabled = retrieveColorOrUnspecified("ComboBox.disabledBackground"),
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = hoverBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("ComboBox.disabledForeground"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = Color.Unspecified,
            borderDisabled = Color.Unspecified,
            borderFocused = Color.Unspecified,
            borderPressed = Color.Unspecified,
            borderHovered = Color.Unspecified,
            iconTint = Color.Unspecified,
            iconTintDisabled = Color.Unspecified,
            iconTintFocused = Color.Unspecified,
            iconTintPressed = Color.Unspecified,
            iconTintHovered = Color.Unspecified,
        )

    val arrowWidth = JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH.dp.safeValue()
    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toNonNegativeDpSize()

    return DropdownStyle(
        colors = colors,
        metrics =
            DropdownMetrics(
                arrowMinSize = DpSize(arrowWidth, minimumSize.height),
                minSize = DpSize(minimumSize.width + arrowWidth, minimumSize.height),
                cornerSize = CornerSize(size = JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc().dp.safeValue()),
                contentPadding = PaddingValues(3.dp), // from
                // com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI.getDefaultComboBoxInsets
                borderWidth = 0.dp,
            ),
        icons = DropdownIcons(chevronDown = AllIconsKeys.General.ChevronDown),
        menuStyle = menuStyle,
    )
}
