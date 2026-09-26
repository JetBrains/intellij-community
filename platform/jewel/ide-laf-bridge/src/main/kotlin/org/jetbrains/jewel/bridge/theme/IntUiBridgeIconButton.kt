// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle

internal fun readIconButtonStyle(): IconButtonStyle =
    IconButtonStyle(
        metrics =
            IconButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp.safeValue() / 2),
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

@ApiStatus.Experimental
@ExperimentalJewelApi
internal fun readTransparentIconButton(): IconButtonStyle =
    IconButtonStyle(
        metrics =
            IconButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp.safeValue() / 2),
                borderWidth = 1.dp,
                padding = PaddingValues(0.dp),
                minSize = DpSize(24.dp, 24.dp),
            ),
        colors =
            IconButtonColors(
                foregroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedForeground"),
                background = Color.Transparent,
                backgroundDisabled = Color.Transparent,
                backgroundSelected = Color.Transparent,
                backgroundSelectedActivated = Color.Transparent,
                backgroundPressed = Color.Transparent,
                backgroundHovered = Color.Transparent,
                backgroundFocused = Color.Transparent,
                border = Color.Transparent,
                borderDisabled = Color.Transparent,
                borderSelected = Color.Transparent,
                borderSelectedActivated = Color.Transparent,
                borderFocused = Color.Unspecified,
                borderPressed = Color.Transparent,
                borderHovered = Color.Transparent,
            ),
    )
