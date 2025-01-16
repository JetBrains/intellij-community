// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@GenerateDataFunctions
public class GfmTableStyling(
    public val colors: GfmTableColors,
    public val metrics: GfmTableMetrics,
    public val headerBaseFontWeight: FontWeight,
) {
    @ExperimentalJewelApi
    public constructor(
        borderColor: Color,
        rowBackgroundColor: Color,
        alternateRowBackgroundColor: Color,
        rowBackgroundStyle: RowBackgroundStyle,
        borderWidth: Dp,
        cellPadding: PaddingValues,
        defaultCellContentAlignment: Alignment.Horizontal,
        headerDefaultCellContentAlignment: Alignment.Horizontal,
        headerBaseFontWeight: FontWeight,
    ) : this(
        colors = GfmTableColors(borderColor, rowBackgroundColor, alternateRowBackgroundColor, rowBackgroundStyle),
        metrics =
            GfmTableMetrics(borderWidth, cellPadding, defaultCellContentAlignment, headerDefaultCellContentAlignment),
        headerBaseFontWeight = headerBaseFontWeight,
    )

    public companion object
}

@GenerateDataFunctions
public class GfmTableColors(
    public val borderColor: Color,
    public val rowBackgroundColor: Color,
    public val alternateRowBackgroundColor: Color,
    public val rowBackgroundStyle: RowBackgroundStyle,
) {
    public companion object
}

public enum class RowBackgroundStyle {
    /**
     * All rows have the same background color, [GfmTableColors.rowBackgroundColor]. In this style,
     * [GfmTableColors.alternateRowBackgroundColor] is ignored.
     */
    Normal,

    /**
     * Rows have alternate colors. Odd rows use [GfmTableColors.rowBackgroundColor] and even rows use
     * [GfmTableColors.alternateRowBackgroundColor].
     */
    Striped,
}

@GenerateDataFunctions
public class GfmTableMetrics(
    public val borderWidth: Dp,
    public val cellPadding: PaddingValues,
    public val defaultCellContentAlignment: Alignment.Horizontal,
    public val headerDefaultCellContentAlignment: Alignment.Horizontal,
) {
    public companion object
}
