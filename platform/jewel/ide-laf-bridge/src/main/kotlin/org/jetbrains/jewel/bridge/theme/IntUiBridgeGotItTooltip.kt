// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.ui.component.gotit.GotItColors
import org.jetbrains.jewel.ui.component.gotit.GotItMetrics
import org.jetbrains.jewel.ui.component.gotit.GotItTooltipStyle
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle

internal fun readGotItTooltipStyle(): GotItTooltipStyle =
    GotItTooltipStyle(
        colors =
            GotItColors(
                foreground = JBUI.CurrentTheme.GotItTooltip.foreground(false).toComposeColor(),
                background = JBUI.CurrentTheme.GotItTooltip.background(false).toComposeColor(),
                stepForeground = JBUI.CurrentTheme.GotItTooltip.stepForeground(false).toComposeColor(),
                secondaryActionForeground =
                    JBUI.CurrentTheme.GotItTooltip.secondaryActionForeground(false).toComposeColor(),
                headerForeground = JBUI.CurrentTheme.GotItTooltip.headerForeground(false).toComposeColor(),
                balloonBorderColor = JBUI.CurrentTheme.GotItTooltip.borderColor(false).toComposeColor(),
                imageBorderColor = JBUI.CurrentTheme.GotItTooltip.imageBorderColor(false).toComposeColor(),
                link = JBUI.CurrentTheme.GotItTooltip.linkForeground(false).toComposeColor(),
                codeForeground = JBUI.CurrentTheme.GotItTooltip.codeForeground(false).toComposeColor(),
                codeBackground = JBUI.CurrentTheme.GotItTooltip.codeBackground(false).toComposeColor(),
            ),
        metrics =
            GotItMetrics(
                contentPadding = JBUI.CurrentTheme.GotItTooltip.insets().toPaddingValues(),
                textPadding = JBUI.CurrentTheme.GotItTooltip.TEXT_INSET.dp.safeValue(),
                buttonPadding =
                    PaddingValues(
                        top = JBUI.CurrentTheme.GotItTooltip.BUTTON_TOP_INSET.dp.safeValue(),
                        bottom = JBUI.CurrentTheme.GotItTooltip.BUTTON_BOTTOM_INSET.dp.safeValue(),
                    ),
                iconPadding = JBUI.CurrentTheme.GotItTooltip.ICON_INSET.dp.safeValue(),
                imagePadding =
                    PaddingValues(
                        top = JBUI.CurrentTheme.GotItTooltip.IMAGE_TOP_INSET.dp.safeValue(),
                        bottom = JBUI.CurrentTheme.GotItTooltip.IMAGE_BOTTOM_INSET.dp.safeValue(),
                    ),
                cornerRadius = JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.dp.safeValue(),
            ),
    )

internal fun readGotItButtonStyle(): ButtonStyle {
    val background = SolidColor(retrieveColorOrUnspecified("GotItTooltip.Button.startBackground"))
    val border = SolidColor(retrieveColorOrUnspecified("GotItTooltip.Button.startBorderColor"))
    val content = JBUI.CurrentTheme.GotItTooltip.buttonForeground().toComposeColor()

    return ButtonStyle(
        colors =
            ButtonColors(
                background = background,
                backgroundDisabled = SolidColor(Color.Unspecified),
                backgroundFocused = background,
                backgroundPressed = background,
                backgroundHovered = background,
                content = content,
                contentDisabled = retrieveColorOrUnspecified("Button.disabledText"),
                contentFocused = content,
                contentPressed = content,
                contentHovered = content,
                border = border,
                borderDisabled = SolidColor(Color.Unspecified),
                borderFocused = border,
                borderPressed = border,
                borderHovered = border,
            ),
        metrics =
            ButtonMetrics(
                cornerSize = CornerSize(JBUI.CurrentTheme.GotItTooltip.CORNER_RADIUS.dp.safeValue()),
                padding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                minSize = DpSize(72.dp, 28.dp),
                borderWidth = 1.dp,
                focusOutlineExpand = 0.dp,
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}
