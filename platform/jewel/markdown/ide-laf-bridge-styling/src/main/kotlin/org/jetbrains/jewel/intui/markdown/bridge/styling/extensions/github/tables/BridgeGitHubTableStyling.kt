// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.tables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.markdown.bridge.styling.isLightTheme
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableColors
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableMetrics
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.RowBackgroundStyle

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableStyling.Companion.create(
    colors: GfmTableColors = GfmTableColors.create(),
    metrics: GfmTableMetrics = GfmTableMetrics.create(),
    headerBaseFontWeight: FontWeight = FontWeight.SemiBold,
): GfmTableStyling = GfmTableStyling(colors, metrics, headerBaseFontWeight)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableColors.Companion.create(
    borderColor: Color = if (isLightTheme) Color(0xffd1d9e0) else Color(0xff3d444d),
    rowBackgroundColor: Color = Color.Unspecified,
    alternateRowBackgroundColor: Color = if (isLightTheme) Color(0xfff6f8fa) else Color(0xff151b23),
    rowBackgroundStyle: RowBackgroundStyle = RowBackgroundStyle.Striped,
): GfmTableColors = GfmTableColors(borderColor, rowBackgroundColor, alternateRowBackgroundColor, rowBackgroundStyle)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableMetrics.Companion.create(
    borderWidth: Dp = 1.dp,
    cellPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
    defaultCellContentAlignment: Alignment.Horizontal = Alignment.Start,
    headerDefaultCellContentAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
): GfmTableMetrics =
    GfmTableMetrics(borderWidth, cellPadding, defaultCellContentAlignment, headerDefaultCellContentAlignment)
