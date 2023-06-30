package org.jetbrains.jewel

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.lazy.SelectableLazyItemScope
import org.jetbrains.jewel.foundation.tree.DefaultTreeViewKeyActions
import org.jetbrains.jewel.foundation.tree.KeyBindingScopedActions
import org.jetbrains.jewel.foundation.tree.Tree
import org.jetbrains.jewel.foundation.tree.TreeState
import org.jetbrains.jewel.foundation.tree.TreeView
import org.jetbrains.jewel.foundation.tree.rememberTreeState

@ExperimentalJewelApi
@Composable
fun <T> IntelliJTree(
    modifier: Modifier = Modifier,
    tree: Tree<T>,
    treeState: TreeState = rememberTreeState(),
    onElementClick: (Tree.Element<T>) -> Unit = { println("click") },
    onElementDoubleClick: (Tree.Element<T>) -> Unit = { println("double click") },
    keyActions: KeyBindingScopedActions = DefaultTreeViewKeyActions(treeState),
    defaults: TreeDefaults = IntelliJTheme.treeDefaults,
    colors: TreeColors = defaults.colors(),
    elementContent: @Composable SelectableLazyItemScope.(Tree.Element<T>) -> Unit
) =
    TreeView(
        modifier = modifier,
        tree = tree,
        treeState = treeState,
        onElementClick = onElementClick,
        onElementDoubleClick = onElementDoubleClick,
        keyActions = keyActions,
        selectionFocusedBackgroundColor = colors.elementBackgroundColor(isFocused = true, isSelected = true).value,
        selectionBackgroundColor = colors.elementBackgroundColor(isFocused = false, isSelected = true).value,
        focusedBackgroundColor = colors.elementBackgroundColor(isFocused = true, isSelected = false).value,
        arrowContent = { isOpen ->
            Box(Modifier.rotate(if (isOpen) 90f else 0f)) {
                Icon(
                    painter = defaults.dropDownArrowIconPainter(),
                    contentDescription = "Dropdown link",
                    tint = colors.nodeIconColor().value
                )
            }
        },
        elementContent = elementContent
    )

internal val LocalTreeDefaults = compositionLocalOf<TreeDefaults> {
    error("TreeDefaults not provided")
}

interface TreeColors {

    @Composable
    fun elementBackgroundColor(isFocused: Boolean, isSelected: Boolean): State<Color>

    @Composable
    fun elementForegroundColor(isFocused: Boolean, isSelected: Boolean): State<Color>

    @Composable
    fun nodeIconColor(): State<Color>
}

interface TreeDefaults {

    @Composable
    fun minWidth(): Dp

    @Composable
    fun minHeight(): Dp

    @Composable
    fun indentPadding(): Dp

    @Composable
    fun colors(): TreeColors

    @Composable
    fun dropDownArrowIconPainter(): Painter
}

@Immutable
private data class DefaultTreeColors(
    val selectedFocusedBackgroundColor: Color,
    val selectedBackgroundColor: Color,
    val focusedBackgroundColor: Color,
    val backgroundColor: Color,
    val foregroundColor: Color,
    val focusedForegroundColor: Color,
    val selectedFocusedForegroundColor: Color,
    val selectedForegroundColor: Color,
    val nodeIconColor: Color
) : TreeColors {

    @Composable
    override fun elementBackgroundColor(isFocused: Boolean, isSelected: Boolean): State<Color> =
        rememberUpdatedState(
            when {
                isSelected && isFocused -> selectedFocusedBackgroundColor
                isSelected -> selectedBackgroundColor
                isFocused -> focusedBackgroundColor
                else -> backgroundColor
            }
        )

    @Composable
    override fun elementForegroundColor(isFocused: Boolean, isSelected: Boolean): State<Color> =
        rememberUpdatedState(
            when {
                isSelected && isFocused -> selectedFocusedForegroundColor
                isSelected -> selectedForegroundColor
                isFocused -> focusedForegroundColor
                else -> foregroundColor
            }
        )

    @Composable
    override fun nodeIconColor() = rememberUpdatedState(nodeIconColor)
}

fun treeColors(
    selectedFocusedBackgroundColor: Color,
    selectedBackgroundColor: Color,
    focusedBackgroundColor: Color,
    backgroundColor: Color,
    selectedFocusedForegroundColor: Color,
    selectedForegroundColor: Color,
    focusedForegroundColor: Color,
    foregroundColor: Color,
    nodeIconColor: Color
): TreeColors = DefaultTreeColors(
    selectedFocusedBackgroundColor = selectedFocusedBackgroundColor,
    selectedBackgroundColor = selectedBackgroundColor,
    focusedBackgroundColor = focusedBackgroundColor,
    backgroundColor = backgroundColor,
    selectedFocusedForegroundColor = selectedFocusedForegroundColor,
    selectedForegroundColor = selectedForegroundColor,
    focusedForegroundColor = focusedForegroundColor,
    foregroundColor = foregroundColor,
    nodeIconColor = nodeIconColor
)
