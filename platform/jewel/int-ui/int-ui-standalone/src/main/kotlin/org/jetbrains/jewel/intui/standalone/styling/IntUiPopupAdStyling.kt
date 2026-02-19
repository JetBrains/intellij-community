// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.component.styling.PopupAdColors
import org.jetbrains.jewel.ui.component.styling.PopupAdMetrics
import org.jetbrains.jewel.ui.component.styling.PopupAdStyle

public fun PopupAdStyle.Companion.light(
    colors: PopupAdColors = PopupAdColors.light(),
    metrics: PopupAdMetrics = PopupAdMetrics.light(),
): PopupAdStyle = PopupAdStyle(colors = colors, metrics = metrics)

public fun PopupAdColors.Companion.light(background: Color = Color(0xFFF2F2F2)): PopupAdColors =
    PopupAdColors(background = background)

public fun PopupAdMetrics.Companion.light(
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    minHeight: Dp = 20.dp,
): PopupAdMetrics = PopupAdMetrics(padding = padding, minHeight = minHeight)

public fun PopupAdStyle.Companion.dark(
    colors: PopupAdColors = PopupAdColors.dark(),
    metrics: PopupAdMetrics = PopupAdMetrics.dark(),
): PopupAdStyle = PopupAdStyle(colors = colors, metrics = metrics)

public fun PopupAdColors.Companion.dark(
    background: Color = IntUiDarkTheme.colors.grayOrNull(2) ?: Color(0xFF2B2B2B)
): PopupAdColors = PopupAdColors(background = background)

public fun PopupAdMetrics.Companion.dark(
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    minHeight: Dp = 20.dp,
): PopupAdMetrics = PopupAdMetrics(padding = padding, minHeight = minHeight)
