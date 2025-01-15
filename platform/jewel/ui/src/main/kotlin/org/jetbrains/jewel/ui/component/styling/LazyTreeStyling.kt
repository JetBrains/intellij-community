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

    public companion object
}

public val LocalLazyTreeStyle: ProvidableCompositionLocal<LazyTreeStyle> = staticCompositionLocalOf {
    error("No LazyTreeStyle provided. Have you forgotten the theme?")
}
