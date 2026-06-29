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

/** Combines [SimpleListItemColors] and [SimpleListItemMetrics] to style a simple list item. */
@GenerateDataFunctions
public class SimpleListItemStyle(
    /** The color tokens for this list item. */
    public val colors: SimpleListItemColors,
    /** The size and spacing metrics for this list item. */
    public val metrics: SimpleListItemMetrics,
) {
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

    /** Companion object for [SimpleListItemStyle]. */
    public companion object
}

/** Holds color tokens for a simple list item in its various selection and activation states. */
@Stable
@GenerateDataFunctions
public class SimpleListItemColors(
    /** The default background color. */
    public val background: Color,
    /** The background color when the item is active (focused container). */
    public val backgroundActive: Color,
    /** The background color when the item is selected. */
    public val backgroundSelected: Color,
    /** The background color when the item is both selected and active. */
    public val backgroundSelectedActive: Color,
    /** The default content (text/icon) color. */
    public val content: Color,
    /** The content color when the item is active. */
    public val contentActive: Color,
    /** The content color when the item is selected. */
    public val contentSelected: Color,
    /** The content color when the item is both selected and active. */
    public val contentSelectedActive: Color,
) {
    /**
     * Returns a [State] holding the content color appropriate for the given [state]: selected-active, selected, active,
     * or default.
     *
     * @param state The current [ListItemState] of the item.
     */
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

    /**
     * Returns a [State] holding the background color appropriate for the given [state]: selected-active, selected,
     * active, or default.
     *
     * @param state The current [ListItemState] of the item.
     */
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

    /** Companion object for [SimpleListItemColors]. */
    public companion object
}

/** Holds size and spacing metrics for a simple list item, including padding, corner size, and icon-text gap. */
@Stable
@GenerateDataFunctions
public class SimpleListItemMetrics(
    /** The padding applied inside the item content area. */
    public val innerPadding: PaddingValues,
    /** The padding applied outside the item, around the selection background. */
    public val outerPadding: PaddingValues,
    /** The corner size of the selection background shape. */
    public val selectionBackgroundCornerSize: CornerSize,
    /** The gap between an icon and its accompanying text. */
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

    /** Companion object for [SimpleListItemMetrics]. */
    public companion object
}

/** CompositionLocal providing the [SimpleListItemStyle] for the current theme. */
public val LocalSimpleListItemStyleStyle: ProvidableCompositionLocal<SimpleListItemStyle> = staticCompositionLocalOf {
    error("No LocalSimpleListItemStyleStyle provided. Have you forgotten the theme?")
}
