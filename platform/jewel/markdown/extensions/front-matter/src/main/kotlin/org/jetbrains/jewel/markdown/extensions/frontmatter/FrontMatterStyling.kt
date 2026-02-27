package org.jetbrains.jewel.markdown.extensions.frontmatter

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/**
 * The styling for a [FrontMatter] block, rendered as a headerless two-column table.
 *
 * @param colors The colors to use for the front matter table.
 * @param metrics The metrics to use for the front matter table.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class FrontMatterStyling(public val colors: FrontMatterColors, public val metrics: FrontMatterMetrics) {
    public constructor(
        borderColor: Color,
        background: Color,
        borderWidth: Dp,
        cellPadding: PaddingValues,
    ) : this(
        colors = FrontMatterColors(borderColor, background),
        metrics = FrontMatterMetrics(borderWidth, cellPadding),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrontMatterStyling

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "FrontMatterStyling(colors=$colors, metrics=$metrics)"

    public companion object
}

@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class FrontMatterColors(public val borderColor: Color, public val background: Color) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrontMatterColors

        if (borderColor != other.borderColor) return false
        if (background != other.background) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderColor.hashCode()
        result = 31 * result + background.hashCode()
        return result
    }

    override fun toString(): String = "FrontMatterColors(borderColor=$borderColor, background=$background)"

    public companion object
}

@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class FrontMatterMetrics(public val borderWidth: Dp, public val cellPadding: PaddingValues) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrontMatterMetrics

        if (borderWidth != other.borderWidth) return false
        if (cellPadding != other.cellPadding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderWidth.hashCode()
        result = 31 * result + cellPadding.hashCode()
        return result
    }

    override fun toString(): String = "FrontMatterMetrics(borderWidth=$borderWidth, cellPadding=$cellPadding)"

    public companion object
}
