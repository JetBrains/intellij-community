@file:OptIn(ExperimentalComposeUiApi::class)

package org.jetbrains.jewel.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import kotlinx.coroutines.launch
import org.jetbrains.jewel.styles.LocalTreeViewStyle
import org.jetbrains.jewel.styles.TreeViewState
import org.jetbrains.jewel.styles.TreeViewStyle
import org.jetbrains.jewel.styles.updateTreeViewAppearanceTransition
import org.jetbrains.jewel.util.appendIf
import org.jetbrains.jewel.util.visibleItemsRange
import org.jetbrains.skiko.hostOs
import java.io.File
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

@Immutable
data class Tree<T>(val heads: List<Element<T>>) {

    sealed class Element<T> {

        abstract val data: T
        abstract val isSelected: Boolean

        abstract fun withSelection(isSelected: Boolean = this.isSelected): Element<T>

        @Immutable
        data class Leaf<T>(override val data: T, override val isSelected: Boolean) : Element<T>() {

            override fun withSelection(isSelected: Boolean) = copy(isSelected = isSelected)
        }

        @Immutable
        data class Node<T>(
            override val data: T,
            override val isSelected: Boolean,
            val isOpen: Boolean = false,
            val children: List<Element<T>>
        ) : Element<T>() {

            override fun withSelection(isSelected: Boolean) = copy(isSelected = isSelected)
        }
    }

    data class ElementWithDepth<T>(val treeElement: Element<T>, val depth: Int)

    val flattenedTree = buildList {
        val stack = heads.map { it.withDepth(0) }.toMutableList()
        while (stack.isNotEmpty()) {
            val next = stack.removeAt(0)
            add(next)
            if (next.treeElement is Element.Node<T> && next.treeElement.isOpen) {
                stack.addAll(0, next.treeElement.children.map { it.withDepth(next.depth + 1) })
            }
        }
    }

    /**
     * Replaces the first occurrence of [old] in this [Tree] while traversing in depth first.
     *
     * @return Always a new [Tree], eventually with [old] replaced with [new].
     */
    fun replaceElement(old: Element<T>, new: Element<T>): Tree<T> = if (old != new) {
        Tree(heads.map { replaceRecursive(old, new, it, ItemFound(false)) })
    } else {
        this
    }

    fun selectOnly(element: Element<T>) =
        Tree(heads.map { replaceAndApplyOnAllRecursive(it) { if (it == element) it.withSelection(true) else it.withSelection(false) } })

    fun selectElements(elements: Set<Element<T>>) = if (elements.isNotEmpty()) {
        Tree(heads.map { replaceAndApplyOnAllRecursive(it) { if (it in elements) it.withSelection(true) else it.withSelection(false) } })
    } else {
        this
    }

    @Suppress("DataClassShouldBeImmutable") // TODO Lamberto don't use mutable data classes if possible
    private data class ItemFound(var value: Boolean)

    private fun replaceAndApplyOnAllRecursive(
        current: Element<T>,
        action: (Element<T>) -> Element<T>
    ): Element<T> = action(current).let {
        when (it) {
            is Element.Leaf -> it
            is Element.Node -> it.copy(children = it.children.map { replaceAndApplyOnAllRecursive(it, action) })
        }
    }

    private fun replaceRecursive(
        old: Element<T>,
        new: Element<T>,
        current: Element<T>,
        found: ItemFound
    ): Element<T> = when {
        found.value -> current
        current == old -> {
            found.value = true
            new
        }

        current is Element.Node<T> -> current.copy(children = current.children.map { replaceRecursive(old, new, it, found) })
        else -> current
    }
}

fun <T> Tree(head: Tree.Element<T>) = Tree(listOf(head))

fun <T> Tree.Element<T>.withDepth(depth: Int) =
    Tree.ElementWithDepth(this, depth)

fun File.asTree(isOpen: Boolean = false) = Tree(asTreeElement(isOpen))
fun Path.asTree(isOpen: Boolean = false) = Tree(toFile().asTreeElement(isOpen))

fun File.asTreeElement(isOpen: Boolean = false): Tree.Element<File> =
    if (isFile) {
        Tree.Element.Leaf(this, false)
    } else {
        Tree.Element.Node(
            data = this,
            isSelected = false,
            isOpen = isOpen,
            children = listFiles()?.sortedBy {
                when {
                    it.isDirectory -> "a"
                    else -> "b"
                } + it.name
            }?.map { it.asTreeElement(isOpen) } ?: emptyList()
        )
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> BaseTreeLayout(
    tree: Tree<T>,
    modifier: Modifier = Modifier,
    onKeyPressed: (KeyEvent, Int, Tree.ElementWithDepth<T>) -> Boolean = { _, _, _ -> false },
    style: TreeViewStyle = LocalTreeViewStyle.current,
    state: LazyListState = rememberLazyListState(),
    focusedTreeElement: Tree.Element<T>? = null,
    onFocusedElementChange: (Tree.Element<T>?) -> Unit,
    onTreeNodeToggle: (Tree.Element.Node<T>) -> Unit,
    onTreeElementClick: (Int, Tree.Element<T>) -> Unit,
    onTreeElementMultiselectChange: (Int, Tree.Element<T>) -> Unit,
    onTreeElementDoubleClick: (Tree.Element<T>) -> Unit,
    rowContent: @Composable RowScope.(Tree.Element<T>) -> Unit
) {
    var isFocused by remember { mutableStateOf(TreeViewState.fromBoolean(false)) }
    val appearance = style.appearance(isFocused)
    val appearanceTransitionState = updateTreeViewAppearanceTransition(appearance)
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.background(appearanceTransitionState.background)
            .onFocusChanged {
                isFocused = TreeViewState.fromBoolean(it.hasFocus)
            }
            .focusProperties { canFocus = false }
            .focusTarget(),
        state = state
    ) {
        itemsIndexed(tree.flattenedTree, key = { _, item -> item }) { index, treeElementWithDepth ->
            val (treeElement, depth) = treeElementWithDepth
            var isElementSelected by remember { mutableStateOf(false) }
            val focusRequester: FocusRequester = remember { FocusRequester() }

            Row(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .appendIf(treeElement.isSelected) { background(appearanceTransitionState.selectedBackground) }
                    .onFocusChanged {
                        isElementSelected = it.isFocused || it.hasFocus
                        if (isElementSelected) onFocusedElementChange(treeElement)
                    }
                    .onKeyEvent { onKeyPressed(it, index, treeElementWithDepth) }
                    .onClick(onDoubleClick = { onTreeElementDoubleClick(treeElement) }) {
                        onTreeElementClick(index, treeElement)
                    }
                    .onClick(keyboardModifiers = {
                        when {
                            hostOs.isWindows || hostOs.isLinux -> isCtrlPressed
                            hostOs.isMacOS -> isMetaPressed
                            else -> false
                        }
                    }) {
                        onTreeElementMultiselectChange(index, treeElement)
                    }
                    .appendIf(focusedTreeElement == treeElement) { border(2.dp, Color.Red) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.padding(start = depth * appearance.indentWidth, end = appearance.arrowEndPadding))
                when (treeElement) {
                    is Tree.Element.Leaf -> {
                        Box(modifier = Modifier.alpha(0f).paint(appearance.arrowPainter()))
                        rowContent(treeElement)
                    }

                    is Tree.Element.Node -> {
                        @Suppress("MagicNumber")
                        Box(
                            modifier = Modifier.rotate(if (treeElement.isOpen) 90f else 0f)
                                .alpha(if (treeElement.children.isEmpty()) 0f else 1f)
                                .paint(appearance.arrowPainter())
                                .onClick(enabled = treeElement.children.isNotEmpty()) {
                                    onTreeNodeToggle(treeElement)
                                }
                        )
                        rowContent(treeElement)
                    }
                }
            }

            if (focusedTreeElement == treeElement) {
                scope.launch {
                    focusRequester.requestFocus()
                }
            }
        }
    }

    // show focused element if present so that it can actually be focused
    if (focusedTreeElement != null) {
        LaunchedEffect(focusedTreeElement) {
            // get index of focusedTreeElement that will be focused
            val indexOfFocusedItem = tree.flattenedTree.indexOfFirst { it.treeElement == focusedTreeElement }

            // if focusedTreeElement has been found in the flattened tree
            if (indexOfFocusedItem >= 0) {
                // drop preloaded first and last item indexes that are not really visible
                // the second dropped is to match IJ tree view behaviour
                val visibleRange = state.visibleItemsRange.drop(2).dropLast(4)

                // if focusedTreeElement is not visible then show it
                if (indexOfFocusedItem !in visibleRange && visibleRange.isNotEmpty()) {
                    when {
                        // if focusedTreeElement is above the shown ones
                        indexOfFocusedItem < visibleRange.first() -> state.scrollToItem(max(0, indexOfFocusedItem - 2))
                        // if focusedTreeElement is below
                        indexOfFocusedItem > visibleRange.last() -> {
                            // indexOfFirstVisibleWhenFocusedTreeElementIsLast
                            val indexOfFirstVisibleElement = indexOfFocusedItem - visibleRange.size
                            state.scrollToItem(min(tree.flattenedTree.lastIndex, indexOfFirstVisibleElement - 1))
                        }
                    }
                }
            }
        }
    }
}

// Temporary name, it is a TreeView but with default behaviours for key presses
// and opening of the nodes
@Composable
fun <T> TreeLayout(
    modifier: Modifier = Modifier,
    onKeyPressed: (KeyEvent, Int, Tree.ElementWithDepth<T>) -> Boolean = { _, _, _ -> false },
    style: TreeViewStyle = LocalTreeViewStyle.current,
    state: LazyListState = rememberLazyListState(),
    tree: Tree<T>,
    onTreeElementDoubleClick: (Tree.Element<T>) -> Unit,
    onTreeChanged: (Tree<T>) -> Unit,
    rowContent: @Composable RowScope.(Tree.Element<T>) -> Unit
) {
    var focusedTreeElement: Tree.Element<T>? by remember { mutableStateOf(null) }
    val onTreeNodeToggle: (Tree.Element.Node<T>) -> Tree<T> = {
        val newTree = tree.replaceElement(it, it.copy(isOpen = !it.isOpen))
        onTreeChanged(newTree)
        newTree
    }

    BaseTreeLayout(
        modifier = modifier,
        onFocusedElementChange = { focusedTreeElement = it },
        onKeyPressed = { keyEvent, index, elementWithDepth ->
            val (element, depth) = elementWithDepth

            fun focusAndSelectSingleIndex(newIndex: Int): Boolean {
                val newTree = tree.selectOnly(tree.flattenedTree[newIndex].treeElement)
                onTreeChanged(newTree)

                // retrieve the item only after invoking selectOnly(), the item will be different since it's copied and modified
                focusedTreeElement = newTree.flattenedTree[newIndex].treeElement
                return true
            }

            when {
                keyEvent.type != KeyEventType.KeyDown -> false
                keyEvent.key == Key.Enter -> {
                    onTreeElementDoubleClick(element)
                    true
                }

                index > 0 && keyEvent.key == Key.DirectionUp -> focusAndSelectSingleIndex(index - 1)
                index < tree.flattenedTree.lastIndex && keyEvent.key == Key.DirectionDown -> focusAndSelectSingleIndex(index + 1)
                keyEvent.key == Key.DirectionRight -> when {
                    element is Tree.Element.Node<T> && !element.isOpen -> {
                        focusedTreeElement = onTreeNodeToggle(element).flattenedTree[index].treeElement
                        true
                    }

                    index < tree.flattenedTree.lastIndex -> focusAndSelectSingleIndex(index + 1)
                    else -> false
                }

                keyEvent.key == Key.DirectionLeft -> when {
                    element is Tree.Element.Node<T> && element.isOpen -> {
                        focusedTreeElement = onTreeNodeToggle(element).flattenedTree[index].treeElement
                        true
                    }

                    index > 0 -> when (element) {
                        !in tree.heads -> {
                            var currentIndex = index
                            val parentDepth = depth - 1
                            while (currentIndex > 0) {
                                currentIndex--
                                if (tree.flattenedTree[currentIndex].depth == parentDepth) {
                                    break
                                }
                            }
                            check(currentIndex >= 0) { "Cannot find parent of $element" }
                            focusAndSelectSingleIndex(currentIndex)
                        }

                        else -> focusAndSelectSingleIndex(0)
                    }

                    else -> false
                }

                keyEvent.key == Key.Home -> focusAndSelectSingleIndex(0)
                keyEvent.key == Key.MoveEnd -> focusAndSelectSingleIndex(tree.flattenedTree.lastIndex)
                keyEvent.key == Key.PageDown -> focusAndSelectSingleIndex(index + state.layoutInfo.visibleItemsInfo.size)
                keyEvent.key == Key.PageUp -> focusAndSelectSingleIndex(index - state.layoutInfo.visibleItemsInfo.size)
                else -> onKeyPressed(keyEvent, index, elementWithDepth)
            }
        },
        style = style,
        state = state,
        focusedTreeElement = focusedTreeElement,
        tree = tree,
        onTreeNodeToggle = { onTreeNodeToggle(it) },
        onTreeElementClick = { index, treeElement ->
            val newTree = tree.selectOnly(treeElement)
            onTreeChanged(newTree)
            focusedTreeElement = newTree.flattenedTree.getOrNull(index)?.treeElement
        },
        onTreeElementMultiselectChange = { index, treeElement ->
            val elements = tree.flattenedTree.filter { it.treeElement.isSelected }.map { it.treeElement }.toSet()
            val newTree = tree.selectElements(if (treeElement.isSelected) elements - treeElement else elements + treeElement)
            onTreeChanged(newTree)
            focusedTreeElement = newTree.flattenedTree.getOrNull(index)?.treeElement
        },
        onTreeElementDoubleClick = onTreeElementDoubleClick,
        rowContent = rowContent
    )
}
