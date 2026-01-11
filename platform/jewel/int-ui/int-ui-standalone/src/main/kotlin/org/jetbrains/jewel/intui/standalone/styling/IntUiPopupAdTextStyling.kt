// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.component.styling.PopupAdTextColors
import org.jetbrains.jewel.ui.component.styling.PopupAdTextMetrics
import org.jetbrains.jewel.ui.component.styling.PopupAdTextStyle

public fun PopupAdTextStyle.Companion.light(
    colors: PopupAdTextColors = PopupAdTextColors.light(),
    metrics: PopupAdTextMetrics = PopupAdTextMetrics.light(),
    textStyle: TextStyle = TextStyle(fontSize = 11.sp),
): PopupAdTextStyle = PopupAdTextStyle(colors = colors, metrics = metrics, textStyle = textStyle)

public fun PopupAdTextColors.Companion.light(
    foreground: Color = Color.Gray,
    background: Color = Color(0xFFF2F2F2),
): PopupAdTextColors = PopupAdTextColors(foreground = foreground, background = background)

public fun PopupAdTextMetrics.Companion.light(
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    minHeight: Dp = 20.dp,
    spacerHeight: Dp = 4.dp,
): PopupAdTextMetrics = PopupAdTextMetrics(padding = padding, minHeight = minHeight, spacerHeight = spacerHeight)

public fun PopupAdTextStyle.Companion.dark(
    colors: PopupAdTextColors = PopupAdTextColors.dark(),
    metrics: PopupAdTextMetrics = PopupAdTextMetrics.dark(),
    textStyle: TextStyle = TextStyle(fontSize = 11.sp),
): PopupAdTextStyle = PopupAdTextStyle(colors = colors, metrics = metrics, textStyle = textStyle)

public fun PopupAdTextColors.Companion.dark(
    foreground: Color = Color.LightGray,
    background: Color = IntUiDarkTheme.colors.grayOrNull(2) ?: Color(0xFF2B2B2B),
): PopupAdTextColors = PopupAdTextColors(foreground = foreground, background = background)

public fun PopupAdTextMetrics.Companion.dark(
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    minHeight: Dp = 20.dp,
    spacerHeight: Dp = 4.dp,
): PopupAdTextMetrics = PopupAdTextMetrics(padding = padding, minHeight = minHeight, spacerHeight = spacerHeight)
