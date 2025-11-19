// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.TableCellColors
import org.jetbrains.jewel.ui.component.styling.TableColors
import org.jetbrains.jewel.ui.component.styling.TableMetrics
import org.jetbrains.jewel.ui.component.styling.TableStyle

public fun TableStyle.Companion.light(
    colors: TableColors = TableColors.light(),
    metrics: TableMetrics = TableMetrics.default(),
): TableStyle = TableStyle(colors, metrics)

public fun TableStyle.Companion.dark(
    colors: TableColors = TableColors.dark(),
    metrics: TableMetrics = TableMetrics.default(),
): TableStyle = TableStyle(colors, metrics)

public fun TableColors.Companion.light(
    cell: TableCellColors = TableCellColors.lightCell(),
    header: TableCellColors = TableCellColors.lightHeader(),
): TableColors = TableColors(cell, header)

public fun TableColors.Companion.dark(
    cell: TableCellColors = TableCellColors.darkCell(),
    header: TableCellColors = TableCellColors.darkHeader(),
): TableColors = TableColors(cell, header)

public fun TableCellColors.Companion.lightCell(
    background: Color = IntUiLightTheme.colors.gray(13),
    backgroundSelected: Color = IntUiLightTheme.colors.blue(11),
    backgroundStripe: Color = IntUiLightTheme.colors.gray(13),
    foreground: Color = IntUiLightTheme.colors.gray(1),
    foregroundSelected: Color = IntUiLightTheme.colors.gray(1),
    foregroundStripe: Color = IntUiLightTheme.colors.gray(1),
    borderColor: Color = IntUiLightTheme.colors.gray(12),
): TableCellColors =
    TableCellColors(
        background = background,
        backgroundSelected = backgroundSelected,
        backgroundStripe = backgroundStripe,
        foreground = foreground,
        foregroundSelected = foregroundSelected,
        foregroundStripe = foregroundStripe,
        borderColor = borderColor,
    )

public fun TableCellColors.Companion.darkCell(
    background: Color = IntUiDarkTheme.colors.gray(2),
    backgroundSelected: Color = IntUiDarkTheme.colors.blue(2),
    backgroundStripe: Color = IntUiDarkTheme.colors.gray(2),
    foreground: Color = IntUiDarkTheme.colors.gray(12),
    foregroundSelected: Color = IntUiDarkTheme.colors.gray(12),
    foregroundStripe: Color = IntUiDarkTheme.colors.gray(12),
    borderColor: Color = IntUiDarkTheme.colors.gray(1),
): TableCellColors =
    TableCellColors(
        background = background,
        backgroundSelected = backgroundSelected,
        backgroundStripe = backgroundStripe,
        foreground = foreground,
        foregroundSelected = foregroundSelected,
        foregroundStripe = foregroundStripe,
        borderColor = borderColor,
    )

public fun TableCellColors.Companion.lightHeader(
    background: Color = IntUiLightTheme.colors.gray(13),
    foreground: Color = IntUiLightTheme.colors.gray(1),
    borderColor: Color = IntUiLightTheme.colors.gray(12),
): TableCellColors = TableCellColors(background = background, foreground = foreground, borderColor = borderColor)

public fun TableCellColors.Companion.darkHeader(
    background: Color = IntUiDarkTheme.colors.gray(2),
    foreground: Color = IntUiDarkTheme.colors.gray(12),
    borderColor: Color = IntUiDarkTheme.colors.gray(1),
): TableCellColors = TableCellColors(background = background, foreground = foreground, borderColor = borderColor)

public fun TableMetrics.Companion.default(borderWidth: Dp = 1.dp, borderWidthSelected: Dp = 1.dp): TableMetrics =
    TableMetrics(borderWidth, borderWidthSelected)
