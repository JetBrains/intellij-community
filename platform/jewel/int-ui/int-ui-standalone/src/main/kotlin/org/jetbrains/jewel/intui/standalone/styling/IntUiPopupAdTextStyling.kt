// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.ui.component.styling.PopupAdTextColors
import org.jetbrains.jewel.ui.component.styling.PopupAdTextMetrics
import org.jetbrains.jewel.ui.component.styling.PopupAdTextStyle

public fun PopupAdTextStyle.Companion.light(): PopupAdTextStyle =
    PopupAdTextStyle(
        colors = PopupAdTextColors(foreground = Color.Gray, background = Color(0xFFF2F2F2)),
        metrics =
            PopupAdTextMetrics(
                padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                minHeight = 20.dp,
                spacerHeight = 4.dp,
            ),
        textStyle = TextStyle(fontSize = 11.sp),
    )

public fun PopupAdTextStyle.Companion.dark(): PopupAdTextStyle =
    PopupAdTextStyle(
        colors =
            PopupAdTextColors(
                foreground = Color.LightGray,
                background = IntUiDarkTheme.colors.grayOrNull(2) ?: Color(0xFF2B2B2B),
            ),
        metrics =
            PopupAdTextMetrics(
                padding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                minHeight = 20.dp,
                spacerHeight = 4.dp,
            ),
        textStyle = TextStyle(fontSize = 11.sp),
    )
