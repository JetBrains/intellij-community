// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Defines the overall visual style of a GFM table, combining its colors, metrics, and header font weight. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class GfmTableStyling(
    /** The color tokens for the table. */
    public val colors: GfmTableColors,
    /** The layout metrics for the table. */
    public val metrics: GfmTableMetrics,
    /** The base font weight applied to header cells. */
    public val headerBaseFontWeight: FontWeight,
) {
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

    /** Companion object for [GfmTableStyling]. */
    public companion object
}

/** Holds the color tokens for a GFM table: border, row background, and alternate row background. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class GfmTableColors(
    /** The color of the table borders. */
    public val borderColor: Color,
    /** The background color for standard (odd) rows. */
    public val rowBackgroundColor: Color,
    /** The background color for alternate (even) rows when using [RowBackgroundStyle.Striped]. */
    public val alternateRowBackgroundColor: Color,
    /** The row background style, controlling whether rows are uniform or striped. */
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

    /** Companion object for [GfmTableColors]. */
    public companion object
}

/** Controls whether GFM table rows are rendered with a uniform or alternating background color. */
@ApiStatus.Experimental
@ExperimentalJewelApi
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

/** Holds the layout metrics for a GFM table: border width, cell padding, and content alignment. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class GfmTableMetrics(
    /** The width of the table borders. */
    public val borderWidth: Dp,
    /** The padding applied inside each table cell. */
    public val cellPadding: PaddingValues,
    /** The default horizontal content alignment for body cells. */
    public val defaultCellContentAlignment: Alignment.Horizontal,
    /** The default horizontal content alignment for header cells. */
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

    /** Companion object for [GfmTableMetrics]. */
    public companion object
}
