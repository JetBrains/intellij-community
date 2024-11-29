package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeWithFallbacks
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toDpSize
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle

internal fun readDefaultButtonStyle(): ButtonStyle {
    val normalBackground =
        listOf(
                JBUI.CurrentTheme.Button.defaultButtonColorStart().toComposeColor(),
                JBUI.CurrentTheme.Button.defaultButtonColorEnd().toComposeColor(),
            )
            .createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Button.default.foreground")

    val normalBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(true).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(true).toComposeColor(),
            )
            .createVerticalBrush()

    val colors =
        ButtonColors(
            background = normalBackground,
            backgroundDisabled = SolidColor(Color.Transparent),
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("Button.disabledText"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = SolidColor(JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor()),
            borderFocused = SolidColor(retrieveColorOrUnspecified("Button.default.focusedBorderColor")),
            borderPressed = normalBorder,
            borderHovered = normalBorder,
        )

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()
    return ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = retrieveArcAsCornerSizeWithFallbacks("Button.default.arc", "Button.arc"),
                padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = 1.dp,
                focusOutlineExpand = 1.5.dp, // From DarculaButtonPainter.getBorderInsets
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}

internal fun readOutlinedButtonStyle(): ButtonStyle {
    val normalBackground =
        listOf(
                JBUI.CurrentTheme.Button.buttonColorStart().toComposeColor(),
                JBUI.CurrentTheme.Button.buttonColorEnd().toComposeColor(),
            )
            .createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Button.foreground")

    val normalBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
            )
            .createVerticalBrush()

    val colors =
        ButtonColors(
            background = normalBackground,
            backgroundDisabled = SolidColor(Color.Transparent),
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("Button.disabledText"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = SolidColor(JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor()),
            borderFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
            borderPressed = normalBorder,
            borderHovered = normalBorder,
        )

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()
    return ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = DarculaUIUtil.LW.dp,
                focusOutlineExpand = Dp.Unspecified,
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}

internal fun readSegmentedControlButtonStyle(): SegmentedControlButtonStyle {
    val selectedBackground = SolidColor(JBUI.CurrentTheme.SegmentedButton.SELECTED_BUTTON_COLOR.toComposeColor())

    val normalBorder =
        listOf(
                JBUI.CurrentTheme.SegmentedButton.SELECTED_START_BORDER_COLOR.toComposeColor(),
                JBUI.CurrentTheme.SegmentedButton.SELECTED_END_BORDER_COLOR.toComposeColor(),
            )
            .createVerticalBrush()

    val selectedDisabledBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
            )
            .createVerticalBrush()

    val colors =
        SegmentedControlButtonColors(
            background = SolidColor(Color.Transparent),
            backgroundPressed = selectedBackground,
            backgroundHovered = SolidColor(JBUI.CurrentTheme.ActionButton.hoverBackground().toComposeColor()),
            backgroundSelected = selectedBackground,
            backgroundSelectedFocused =
                SolidColor(JBUI.CurrentTheme.SegmentedButton.FOCUSED_SELECTED_BUTTON_COLOR.toComposeColor()),
            content = retrieveColorOrUnspecified("Button.foreground"),
            contentDisabled = retrieveColorOrUnspecified("Label.disabledForeground"),
            border = normalBorder,
            borderSelected = normalBorder,
            borderSelectedDisabled = selectedDisabledBorder,
            borderSelectedFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
        )

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()
    return SegmentedControlButtonStyle(
        colors = colors,
        metrics =
            SegmentedControlButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                segmentedButtonPadding = PaddingValues(horizontal = 14.dp),
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
    )
}

internal fun readIconButtonStyle(): IconButtonStyle =
    IconButtonStyle(
        metrics =
            IconButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                borderWidth = 1.dp,
                padding = PaddingValues(0.dp),
                minSize = DpSize(24.dp, 24.dp),
            ),
        colors =
            IconButtonColors(
                foregroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedForeground"),
                background = Color.Unspecified,
                backgroundDisabled = Color.Unspecified,
                backgroundSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                backgroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
                backgroundPressed = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                backgroundHovered = retrieveColorOrUnspecified("ActionButton.hoverBackground"),
                backgroundFocused = retrieveColorOrUnspecified("ActionButton.hoverBackground"),
                border = Color.Unspecified,
                borderDisabled = Color.Unspecified,
                borderSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                borderSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
                borderFocused = Color.Unspecified,
                borderPressed = retrieveColorOrUnspecified("ActionButton.pressedBorderColor"),
                borderHovered = retrieveColorOrUnspecified("ActionButton.hoverBorderColor"),
            ),
    )
