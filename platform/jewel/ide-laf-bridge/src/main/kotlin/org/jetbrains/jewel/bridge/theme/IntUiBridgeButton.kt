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
import org.jetbrains.jewel.bridge.retrieveArcAsNonNegativeCornerSizeOrDefault
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toNonNegativeDpSize
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle

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

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toNonNegativeDpSize()
    return ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = retrieveArcAsNonNegativeCornerSizeOrDefault("Button.default.arc", buttonCornerSize()),
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

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toNonNegativeDpSize()
    return ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = buttonCornerSize(),
                padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = borderWidth,
                focusOutlineExpand = Dp.Unspecified,
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}

private fun buttonCornerSize(): CornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp.safeValue() / 2)
