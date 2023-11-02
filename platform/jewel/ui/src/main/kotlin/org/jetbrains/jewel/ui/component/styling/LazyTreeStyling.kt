package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
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
import org.jetbrains.jewel.ui.painter.PainterProvider

@Stable
@GenerateDataFunctions
public class LazyTreeStyle(
    public val colors: LazyTreeColors,
    public val metrics: LazyTreeMetrics,
    public val icons: LazyTreeIcons,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LazyTreeColors(
    public val elementBackgroundFocused: Color,
    public val elementBackgroundSelected: Color,
    public val elementBackgroundSelectedFocused: Color,
    public val content: Color,
    public val contentFocused: Color,
    public val contentSelected: Color,
    public val contentSelectedFocused: Color,
) {

    @Composable
    public fun contentFor(state: TreeElementState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected && state.isFocused -> contentSelectedFocused
                state.isFocused -> contentFocused
                state.isSelected -> contentSelected
                else -> content
            },
        )

    public companion object
}

@Stable
@GenerateDataFunctions
public class LazyTreeMetrics(
    public val indentSize: Dp,
    public val elementBackgroundCornerSize: CornerSize,
    public val elementPadding: PaddingValues,
    public val elementContentPadding: PaddingValues,
    public val elementMinHeight: Dp,
    public val chevronContentGap: Dp,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LazyTreeIcons(
    public val chevronCollapsed: PainterProvider,
    public val chevronExpanded: PainterProvider,
    public val chevronSelectedCollapsed: PainterProvider,
    public val chevronSelectedExpanded: PainterProvider,
) {

    @Composable
    public fun chevron(isExpanded: Boolean, isSelected: Boolean): PainterProvider =
        when {
            isSelected && isExpanded -> chevronSelectedExpanded
            isSelected && !isExpanded -> chevronSelectedCollapsed
            !isSelected && isExpanded -> chevronExpanded
            else -> chevronCollapsed
        }

    public companion object
}

public val LocalLazyTreeStyle: ProvidableCompositionLocal<LazyTreeStyle> =
    staticCompositionLocalOf {
        error("No LazyTreeStyle provided. Have you forgotten the theme?")
    }
