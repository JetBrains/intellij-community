// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.TableCellState

@Stable
@GenerateDataFunctions
public class TableStyle(public val colors: TableColors, public val metrics: TableMetrics) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TableStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "TableStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@Stable
@GenerateDataFunctions
public class TableColors(public val cell: TableCellColors, public val header: TableCellColors) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TableColors

        if (cell != other.cell) return false
        if (header != other.header) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cell.hashCode()
        result = 31 * result + header.hashCode()
        return result
    }

    override fun toString(): String = "TableColors(cell=$cell, header=$header)"

    public companion object
}

@Stable
@GenerateDataFunctions
public class TableMetrics(public val borderWidth: Dp, public val borderWidthSelected: Dp) {
    @Composable
    public fun borderWidthFor(state: TableCellState): State<Dp> =
        rememberUpdatedState(
            when {
                state.isSelected -> borderWidthSelected
                else -> borderWidth
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TableMetrics

        if (borderWidth != other.borderWidth) return false
        if (borderWidthSelected != other.borderWidthSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderWidth.hashCode()
        result = 31 * result + borderWidthSelected.hashCode()
        return result
    }

    override fun toString(): String = "TableMetrics(borderWidth=$borderWidth, borderWidthSelected=$borderWidthSelected)"

    public companion object
}

@Stable
@GenerateDataFunctions
public class TableCellColors(
    public val background: Color,
    public val backgroundSelected: Color,
    public val backgroundStripe: Color,
    public val foreground: Color,
    public val foregroundSelected: Color,
    public val foregroundStripe: Color,
    public val borderColor: Color,
) {
    public constructor(
        background: Color,
        foreground: Color,
        borderColor: Color,
    ) : this(background, background, background, foreground, foreground, foreground, borderColor)

    @Composable
    public fun backgroundFor(state: TableCellState, isStripe: Boolean): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected -> backgroundSelected
                isStripe -> backgroundStripe
                else -> background
            }
        )

    @Composable
    public fun contentFor(state: TableCellState, isStripe: Boolean): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected -> foregroundSelected
                isStripe -> foregroundStripe
                else -> foreground
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TableCellColors

        if (background != other.background) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (backgroundStripe != other.backgroundStripe) return false
        if (foreground != other.foreground) return false
        if (foregroundSelected != other.foregroundSelected) return false
        if (foregroundStripe != other.foregroundStripe) return false
        if (borderColor != other.borderColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + backgroundStripe.hashCode()
        result = 31 * result + foreground.hashCode()
        result = 31 * result + foregroundSelected.hashCode()
        result = 31 * result + foregroundStripe.hashCode()
        result = 31 * result + borderColor.hashCode()
        return result
    }

    override fun toString(): String =
        "TableCellColors(" +
            "background=$background, " +
            "backgroundSelected=$backgroundSelected, " +
            "backgroundStripe=$backgroundStripe, " +
            "foreground=$foreground, " +
            "foregroundSelected=$foregroundSelected, " +
            "foregroundStripe=$foregroundStripe, " +
            "borderColor=$borderColor" +
            ")"

    public companion object
}

internal val LocalTableStyle: ProvidableCompositionLocal<TableStyle> = staticCompositionLocalOf {
    error("No TableStyle provided. Have you forgotten the theme?")
}

internal fun fallbackTableStyle(): TableStyle =
    TableStyle(
        colors =
            TableColors(
                cell =
                    TableCellColors(
                        background = Color(0xFFF7F8FA), // Gray13
                        backgroundSelected = Color(0xFFD4E2FF), // Blue11
                        backgroundStripe = Color(0xFFF7F8FA), // Gray13
                        foreground = Color(0xFF000000), // Gray1
                        foregroundSelected = Color(0xFF000000), // Gray1,
                        foregroundStripe = Color(0xFF000000), // Gray1
                        borderColor = Color(0xFFEBECF0), // Gray12
                    ),
                header =
                    TableCellColors(
                        background = Color(0xFFF7F8FA), // Gray13
                        foreground = Color(0xFF000000), // Gray1
                        borderColor = Color(0xFFEBECF0), // Gray12
                    ),
            ),
        metrics = TableMetrics(1.dp, 1.dp),
    )
