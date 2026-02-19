package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ListItemState

@GenerateDataFunctions
public class SimpleListItemStyle(public val colors: SimpleListItemColors, public val metrics: SimpleListItemMetrics) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleListItemStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        return result
    }

    override fun toString(): String = "SimpleListItemStyle(colors=$colors, metrics=$metrics)"

    public companion object
}

@Stable
@GenerateDataFunctions
public class SimpleListItemColors(
    public val background: Color,
    public val backgroundActive: Color,
    public val backgroundSelected: Color,
    public val backgroundSelectedActive: Color,
    public val content: Color,
    public val contentActive: Color,
    public val contentSelected: Color,
    public val contentSelectedActive: Color,
) {
    @Composable
    public fun contentFor(state: ListItemState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected && state.isActive -> contentSelectedActive
                state.isSelected && !state.isActive -> contentSelected
                state.isActive -> contentActive
                else -> content
            }
        )

    @Composable
    public fun backgroundFor(state: ListItemState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected && state.isActive -> backgroundSelectedActive
                state.isSelected && !state.isActive -> backgroundSelected
                state.isActive -> backgroundActive
                else -> background
            }
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleListItemColors

        if (background != other.background) return false
        if (backgroundActive != other.backgroundActive) return false
        if (backgroundSelected != other.backgroundSelected) return false
        if (backgroundSelectedActive != other.backgroundSelectedActive) return false
        if (content != other.content) return false
        if (contentActive != other.contentActive) return false
        if (contentSelected != other.contentSelected) return false
        if (contentSelectedActive != other.contentSelectedActive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundActive.hashCode()
        result = 31 * result + backgroundSelected.hashCode()
        result = 31 * result + backgroundSelectedActive.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentActive.hashCode()
        result = 31 * result + contentSelected.hashCode()
        result = 31 * result + contentSelectedActive.hashCode()
        return result
    }

    override fun toString(): String {
        return "SimpleListItemColors(" +
            "background=$background, " +
            "backgroundActive=$backgroundActive, " +
            "backgroundSelected=$backgroundSelected, " +
            "backgroundSelectedActive=$backgroundSelectedActive, " +
            "content=$content, " +
            "contentActive=$contentActive, " +
            "contentSelected=$contentSelected, " +
            "contentSelectedActive=$contentSelectedActive" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class SimpleListItemMetrics(
    public val innerPadding: PaddingValues,
    public val outerPadding: PaddingValues,
    public val selectionBackgroundCornerSize: CornerSize,
    public val iconTextGap: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimpleListItemMetrics

        if (innerPadding != other.innerPadding) return false
        if (outerPadding != other.outerPadding) return false
        if (selectionBackgroundCornerSize != other.selectionBackgroundCornerSize) return false
        if (iconTextGap != other.iconTextGap) return false

        return true
    }

    override fun hashCode(): Int {
        var result = innerPadding.hashCode()
        result = 31 * result + outerPadding.hashCode()
        result = 31 * result + selectionBackgroundCornerSize.hashCode()
        result = 31 * result + iconTextGap.hashCode()
        return result
    }

    override fun toString(): String {
        return "SimpleListItemMetrics(" +
            "innerPadding=$innerPadding, " +
            "outerPadding=$outerPadding, " +
            "selectionBackgroundCornerSize=$selectionBackgroundCornerSize, " +
            "iconTextGap=$iconTextGap" +
            ")"
    }

    public companion object
}

public val LocalSimpleListItemStyleStyle: ProvidableCompositionLocal<SimpleListItemStyle> = staticCompositionLocalOf {
    error("No LocalSimpleListItemStyleStyle provided. Have you forgotten the theme?")
}
