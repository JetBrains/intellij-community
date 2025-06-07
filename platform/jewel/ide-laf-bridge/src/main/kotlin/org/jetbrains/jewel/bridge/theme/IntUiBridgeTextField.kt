package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toDpSize
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldColors
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle

internal fun readTextFieldStyle(): TextFieldStyle {
    val normalBackground = retrieveColorOrUnspecified("TextField.background")
    val normalContent = retrieveColorOrUnspecified("TextField.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextField.caretForeground")

    val colors =
        TextFieldColors(
            background = normalBackground,
            backgroundDisabled = Color.Unspecified,
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("TextField.inactiveForeground"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = DarculaUIUtil.getOutlineColor(false, false).toComposeColor(),
            borderFocused = focusedBorder,
            borderPressed = focusedBorder,
            borderHovered = normalBorder,
            caret = normalCaret,
            caretDisabled = normalCaret,
            caretFocused = normalCaret,
            caretPressed = normalCaret,
            caretHovered = normalCaret,
            placeholder = NamedColorUtil.getInactiveTextColor().toComposeColor(),
        )

    val minimumSize = JBUI.CurrentTheme.TextField.minimumSize().toDpSize()
    return TextFieldStyle(
        colors = colors,
        metrics =
            TextFieldMetrics(
                cornerSize = componentArc,
                contentPadding = PaddingValues(horizontal = 8.dp + DarculaUIUtil.LW.dp),
                minSize = DpSize(144.dp, minimumSize.height),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
        iconButtonStyle =
            readIconButtonStyle()
                .copy(
                    colors =
                        IconButtonColors(
                            foregroundSelectedActivated =
                                retrieveColorOrUnspecified("ToolWindow.Button.selectedForeground"),
                            background = Color.Unspecified,
                            backgroundDisabled = Color.Unspecified,
                            backgroundSelected = Color.Unspecified,
                            backgroundSelectedActivated = Color.Unspecified,
                            backgroundFocused = Color.Unspecified,
                            backgroundPressed = Color.Unspecified,
                            backgroundHovered = Color.Unspecified,
                            border = Color.Unspecified,
                            borderDisabled = Color.Unspecified,
                            borderSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                            borderSelectedActivated =
                                retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
                            borderFocused = Color.Unspecified,
                            borderPressed = retrieveColorOrUnspecified("ActionButton.pressedBorderColor"),
                            borderHovered = retrieveColorOrUnspecified("ActionButton.hoverBorderColor"),
                        )
                ),
    )
}

private fun IconButtonStyle.copy(metrics: IconButtonMetrics = this.metrics, colors: IconButtonColors = this.colors) =
    IconButtonStyle(metrics = metrics, colors = colors)
