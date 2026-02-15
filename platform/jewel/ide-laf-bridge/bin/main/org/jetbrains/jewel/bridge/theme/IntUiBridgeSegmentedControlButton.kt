// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toNonNegativeDpSize
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle

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
            border = SolidColor(Color.Transparent),
            borderSelected = normalBorder,
            borderSelectedDisabled = selectedDisabledBorder,
            borderSelectedFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
        )

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toNonNegativeDpSize()
    return SegmentedControlButtonStyle(
        colors = colors,
        metrics =
            SegmentedControlButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp.safeValue() / 2),
                segmentedButtonPadding = PaddingValues(horizontal = 14.dp),
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = borderWidth,
            ),
    )
}
