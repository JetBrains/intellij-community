package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.DividerStyle

@Composable
public fun DividerStyle.Companion.light(
    color: Color = IntUiLightTheme.colors.gray(12),
    metrics: DividerMetrics = DividerMetrics.defaults(),
): DividerStyle = DividerStyle(color, metrics)

@Composable
public fun DividerStyle.Companion.dark(
    color: Color = IntUiDarkTheme.colors.gray(1),
    metrics: DividerMetrics = DividerMetrics.defaults(),
): DividerStyle = DividerStyle(color, metrics)
