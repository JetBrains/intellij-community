package org.jetbrains.jewel.styles

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPainters
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.PainterProvider

typealias TreeViewStyle = ControlStyle<TreeViewAppearance, TreeViewState>

enum class TreeViewState {
    FOCUSED, NOT_FOCUSED;

    companion object {

        fun fromBoolean(hasFocus: Boolean) =
            if (hasFocus) FOCUSED else NOT_FOCUSED
    }
}

data class TreeViewAppearance(
    val arrowPainter: PainterProvider,
    val arrowEndPadding: Dp,
    val indentWidth: Dp,
    val selectedBackground: Color,
    val background: Color
)

val LocalTreeViewStyle = compositionLocalOf<TreeViewStyle> { localNotProvided() }

val Styles.treeView: TreeViewStyle
    @Composable @ReadOnlyComposable get() = LocalTreeViewStyle.current

class TreeViewAppearanceTransitionState(selectedBackground: State<Color>, background: State<Color>) {

    val selectedBackground by selectedBackground
    val background by background
}

@Composable
fun updateTreeViewAppearanceTransition(appearance: TreeViewAppearance): TreeViewAppearanceTransitionState {
    val transition = updateTransition(appearance)
    return TreeViewAppearanceTransitionState(
        transition.animateColor(label = "TreeSelectedItemBackground") { it.selectedBackground },
        transition.animateColor(label = "TreeBackground") { it.background },
    )
}

fun TreeViewStyle(
    palette: IntelliJPalette,
    metrics: IntelliJMetrics,
    painters: IntelliJPainters
) = TreeViewStyle {
    val default = TreeViewAppearance(
        arrowPainter = painters.treeView.arrow,
        arrowEndPadding = metrics.treeView.arrowEndPadding,
        indentWidth = metrics.treeView.indentWidth,
        selectedBackground = palette.treeView.focusedSelectedElementBackground,
        background = palette.treeView.background
    )
    default {
        state(TreeViewState.FOCUSED, default)
        state(TreeViewState.NOT_FOCUSED, default.copy(selectedBackground = palette.treeView.focusedSelectedElementBackground.copy(alpha = 0.6f)))
    }
}
