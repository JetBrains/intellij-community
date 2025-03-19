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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GfmTableStyling

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (headerBaseFontWeight != other.headerBaseFontWeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + headerBaseFontWeight.hashCode()
        return result
    }

    override fun toString(): String =
        "GfmTableStyling(" +
            "colors=$colors, " +
            "metrics=$metrics, " +
            "headerBaseFontWeight=$headerBaseFontWeight" +
            ")"

    public companion object
}

@GenerateDataFunctions
public class GfmTableColors(
    public val borderColor: Color,
    public val rowBackgroundColor: Color,
    public val alternateRowBackgroundColor: Color,
    public val rowBackgroundStyle: RowBackgroundStyle,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GfmTableColors

        if (borderColor != other.borderColor) return false
        if (rowBackgroundColor != other.rowBackgroundColor) return false
        if (alternateRowBackgroundColor != other.alternateRowBackgroundColor) return false
        if (rowBackgroundStyle != other.rowBackgroundStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderColor.hashCode()
        result = 31 * result + rowBackgroundColor.hashCode()
        result = 31 * result + alternateRowBackgroundColor.hashCode()
        result = 31 * result + rowBackgroundStyle.hashCode()
        return result
    }

    override fun toString(): String =
        "GfmTableColors(" +
            "borderColor=$borderColor, " +
            "rowBackgroundColor=$rowBackgroundColor, " +
            "alternateRowBackgroundColor=$alternateRowBackgroundColor, " +
            "rowBackgroundStyle=$rowBackgroundStyle" +
            ")"

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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GfmTableMetrics

        if (borderWidth != other.borderWidth) return false
        if (cellPadding != other.cellPadding) return false
        if (defaultCellContentAlignment != other.defaultCellContentAlignment) return false
        if (headerDefaultCellContentAlignment != other.headerDefaultCellContentAlignment) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderWidth.hashCode()
        result = 31 * result + cellPadding.hashCode()
        result = 31 * result + defaultCellContentAlignment.hashCode()
        result = 31 * result + headerDefaultCellContentAlignment.hashCode()
        return result
    }

    override fun toString(): String {
        return "GfmTableMetrics(" +
            "borderWidth=$borderWidth, " +
            "cellPadding=$cellPadding, " +
            "defaultCellContentAlignment=$defaultCellContentAlignment, " +
            "headerDefaultCellContentAlignment=$headerDefaultCellContentAlignment" +
            ")"
    }

    public companion object
}
