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
@Stable
@GenerateDataFunctions
public class LazyTreeStyle(
    public val colors: SimpleListItemColors,
    public val metrics: LazyTreeMetrics,
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

    public companion object
}

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

@Stable
@GenerateDataFunctions
public class LazyTreeMetrics(
    public val indentSize: Dp,
    public val elementMinHeight: Dp,
    public val chevronContentGap: Dp,
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LazyTreeIcons(
    public val chevronCollapsed: IconKey,
    public val chevronExpanded: IconKey,
    public val chevronSelectedCollapsed: IconKey,
    public val chevronSelectedExpanded: IconKey,
) {
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

    public companion object
}

public val LocalLazyTreeStyle: ProvidableCompositionLocal<LazyTreeStyle> = staticCompositionLocalOf {
    error("No LazyTreeStyle provided. Have you forgotten the theme?")
}
