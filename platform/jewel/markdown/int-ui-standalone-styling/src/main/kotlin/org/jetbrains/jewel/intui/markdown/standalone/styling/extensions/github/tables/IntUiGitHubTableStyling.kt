package org.jetbrains.jewel.intui.markdown.standalone.styling.extensions.github.tables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableColors
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableMetrics
import org.jetbrains.jewel.markdown.extensions.github.tables.GfmTableStyling
import org.jetbrains.jewel.markdown.extensions.github.tables.RowBackgroundStyle

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableStyling.Companion.light(
    colors: GfmTableColors = GfmTableColors.light(),
    metrics: GfmTableMetrics = GfmTableMetrics.defaults(),
    headerBaseFontWeight: FontWeight = FontWeight.SemiBold,
): GfmTableStyling = GfmTableStyling(colors, metrics, headerBaseFontWeight)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableStyling.Companion.dark(
    colors: GfmTableColors = GfmTableColors.dark(),
    metrics: GfmTableMetrics = GfmTableMetrics.defaults(),
    headerBaseFontWeight: FontWeight = FontWeight.SemiBold,
): GfmTableStyling = GfmTableStyling(colors, metrics, headerBaseFontWeight)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableColors.Companion.light(
    borderColor: Color = Color(0xffd1d9e0),
    rowBackgroundColor: Color = Color.Unspecified,
    alternateRowBackgroundColor: Color = Color(0xfff6f8fa),
    rowBackgroundStyle: RowBackgroundStyle = RowBackgroundStyle.Striped,
): GfmTableColors = GfmTableColors(borderColor, rowBackgroundColor, alternateRowBackgroundColor, rowBackgroundStyle)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableColors.Companion.dark(
    borderColor: Color = Color(0xff3d444d),
    rowBackgroundColor: Color = Color.Unspecified,
    alternateRowBackgroundColor: Color = Color(0xff151b23),
    rowBackgroundStyle: RowBackgroundStyle = RowBackgroundStyle.Striped,
): GfmTableColors = GfmTableColors(borderColor, rowBackgroundColor, alternateRowBackgroundColor, rowBackgroundStyle)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun GfmTableMetrics.Companion.defaults(
    borderWidth: Dp = 1.dp,
    cellPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
    defaultCellContentAlignment: Alignment.Horizontal = Alignment.Start,
    headerDefaultCellContentAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
): GfmTableMetrics =
    GfmTableMetrics(borderWidth, cellPadding, defaultCellContentAlignment, headerDefaultCellContentAlignment)
