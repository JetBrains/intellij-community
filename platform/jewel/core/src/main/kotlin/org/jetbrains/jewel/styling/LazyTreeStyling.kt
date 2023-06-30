package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.tree.TreeElementState
import org.jetbrains.jewel.painterResource

@Stable
interface LazyTreeStyle {

    val colors: LazyTreeColors
    val metrics: LazyTreeMetrics
    val icons: LazyTreeIcons
}

@Immutable
interface LazyTreeColors {

    val elementBackgroundFocused: Color
    val elementBackgroundSelected: Color
    val elementBackgroundSelectedFocused: Color

    val chevronTint: Color
    val chevronTintSelected: Color
    val chevronTintFocused: Color
    val chevronTintSelectedFocused: Color

    @Composable
    fun chevronTintFor(state: TreeElementState) = rememberUpdatedState(
        when {
            state.isSelected && state.isFocused -> chevronTintSelectedFocused
            state.isFocused -> chevronTintFocused
            state.isSelected -> chevronTintSelected
            else -> chevronTint
        }
    )
}

@Stable
interface LazyTreeMetrics {
    val indentSize: Dp
    val elementBackgroundCornerSize: CornerSize
    val elementPadding: PaddingValues
    val elementContentPadding: PaddingValues
    val elementMinHeight: Dp
    val chevronContentGap: Dp
}

@Immutable
interface LazyTreeIcons {

    val nodeChevron: String

    @Composable
    fun nodeChevronPainter(resourceLoader: ResourceLoader) = painterResource(nodeChevron, resourceLoader)
}

val LocalLazyTreeStyle = staticCompositionLocalOf<LazyTreeStyle> {
    error("No LazyTreeStyle provided")
}
