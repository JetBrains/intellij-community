package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.lazy.tree.TreeElementState
import org.jetbrains.jewel.ui.icon.IconKey

// TODO: Composition with SimpleItemStyle
/** Combines the colors, metrics, and icons that style a lazy tree component. */
@Stable
@GenerateDataFunctions
public class LazyTreeStyle(
    /** The colors used to render list items in the tree. */
    public val colors: SimpleListItemColors,
    /** The size and spacing metrics for the tree. */
    public val metrics: LazyTreeMetrics,
    /** The icons used for the tree chevrons. */
    public val icons: LazyTreeIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LazyTreeStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        return result
    }

    override fun toString(): String = "LazyTreeStyle(colors=$colors, metrics=$metrics, icons=$icons)"

    /** Companion object for [LazyTreeStyle]. */
    public companion object
}

/** Returns a [State] holding the content color appropriate for the given [state]. */
@Composable
public fun SimpleListItemColors.contentFor(state: TreeElementState): State<Color> =
    rememberUpdatedState(
        when {
            state.isSelected && state.isFocused -> contentSelectedActive
            state.isFocused -> contentActive
            state.isSelected -> contentSelected
            else -> content
        }
    )

/** Holds size and spacing metrics for the lazy tree component. */
@Stable
@GenerateDataFunctions
public class LazyTreeMetrics(
    /** The horizontal indentation per tree depth level. */
    public val indentSize: Dp,
    /** The minimum height of a single tree element row. */
    public val elementMinHeight: Dp,
    /** The gap between the chevron icon and the row content. */
    public val chevronContentGap: Dp,
    /** The metrics applied to each simple list item within the tree. */
    public val simpleListItemMetrics: SimpleListItemMetrics,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LazyTreeMetrics

        if (indentSize != other.indentSize) return false
        if (elementMinHeight != other.elementMinHeight) return false
        if (chevronContentGap != other.chevronContentGap) return false
        if (simpleListItemMetrics != other.simpleListItemMetrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = indentSize.hashCode()
        result = 31 * result + elementMinHeight.hashCode()
        result = 31 * result + chevronContentGap.hashCode()
        result = 31 * result + simpleListItemMetrics.hashCode()
        return result
    }

    override fun toString(): String {
        return "LazyTreeMetrics(" +
            "indentSize=$indentSize, " +
            "elementMinHeight=$elementMinHeight, " +
            "chevronContentGap=$chevronContentGap, " +
            "simpleListItemMetrics=$simpleListItemMetrics" +
            ")"
    }

    /** Companion object for [LazyTreeMetrics]. */
    public companion object
}

/** Holds icon keys for the chevron in its collapsed, expanded, selected-collapsed, and selected-expanded states. */
@Immutable
@GenerateDataFunctions
public class LazyTreeIcons(
    /** The icon key for the chevron in the collapsed, unselected state. */
    public val chevronCollapsed: IconKey,
    /** The icon key for the chevron in the expanded, unselected state. */
    public val chevronExpanded: IconKey,
    /** The icon key for the chevron in the collapsed, selected state. */
    public val chevronSelectedCollapsed: IconKey,
    /** The icon key for the chevron in the expanded, selected state. */
    public val chevronSelectedExpanded: IconKey,
) {
    /** Returns the [IconKey] for the chevron icon appropriate for the given [isExpanded] and [isSelected] states. */
    @Composable
    public fun chevron(isExpanded: Boolean, isSelected: Boolean): IconKey =
        when {
            isSelected && isExpanded -> chevronSelectedExpanded
            isSelected && !isExpanded -> chevronSelectedCollapsed
            !isSelected && isExpanded -> chevronExpanded
            else -> chevronCollapsed
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LazyTreeIcons

        if (chevronCollapsed != other.chevronCollapsed) return false
        if (chevronExpanded != other.chevronExpanded) return false
        if (chevronSelectedCollapsed != other.chevronSelectedCollapsed) return false
        if (chevronSelectedExpanded != other.chevronSelectedExpanded) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chevronCollapsed.hashCode()
        result = 31 * result + chevronExpanded.hashCode()
        result = 31 * result + chevronSelectedCollapsed.hashCode()
        result = 31 * result + chevronSelectedExpanded.hashCode()
        return result
    }

    override fun toString(): String {
        return "LazyTreeIcons(" +
            "chevronCollapsed=$chevronCollapsed, " +
            "chevronExpanded=$chevronExpanded, " +
            "chevronSelectedCollapsed=$chevronSelectedCollapsed, " +
            "chevronSelectedExpanded=$chevronSelectedExpanded" +
            ")"
    }

    /** Companion object for [LazyTreeIcons]. */
    public companion object
}

/** Composition local providing the current [LazyTreeStyle]. */
public val LocalLazyTreeStyle: ProvidableCompositionLocal<LazyTreeStyle> = staticCompositionLocalOf {
    error("No LazyTreeStyle provided. Have you forgotten the theme?")
}
