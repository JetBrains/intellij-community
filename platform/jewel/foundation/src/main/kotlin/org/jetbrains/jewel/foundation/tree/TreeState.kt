package org.jetbrains.jewel.foundation.tree

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.utils.Log
import kotlin.properties.Delegates

@Composable
fun rememberTreeState(selectionMode: SelectionMode = SelectionMode.Multiple) = remember {
    TreeState(
        SelectableLazyListState(
            LazyListState(),
            selectionMode
        )
    )
}

class TreeState(
    internal val delegate: SelectableLazyListState,
) {

    val lazyListState get() = delegate.lazyListState

    val lastFocusedIndex get() = delegate.lastFocusedIndex
    val selectedItemIndexes get() = delegate.selectedItemIndexes

    val selectedElements
        get() = buildList<Tree.Element<*>> {
            selectedItemIndexes.forEach {
                flattenedTree[it]
            }
        }

    //    val isFocused get() = delegate.isFocused
    var flattenedTree by mutableStateOf<List<Tree.Element<*>>>(emptyList())

    internal val openNodesMap = mutableStateMapOf<List<Any>, Tree.Element.Node<*>>()

    internal var lastKeyEventUsedMouse = false

    internal var tree: Tree<*> by Delegates.notNull()

    val openNodes
        get() = openNodesMap.toMap()

    // end workaround

    fun attachTree(tree: Tree<*>) {
        this.tree = tree
        refreshFlattenTree()
    }

    fun openNode(element: Tree.Element.Node<*>, reloadChildren: Boolean = false): Boolean {
        val indexInFlattenTree = flattenedTree.indexOf(element)
        if (indexInFlattenTree < 0) return false
        return doOpenNode(element, reloadChildren)
    }

    @Suppress("unused")
    fun openNode(nodeId: Int, reloadChildren: Boolean = false): Boolean {
        val indexInFlattenTree = flattenedTree.indexOfFirst { it is Tree.Element.Node && it.id == nodeId }
        if (indexInFlattenTree < 0) return false
        return doOpenNode(flattenedTree[indexInFlattenTree] as Tree.Element.Node<*>, reloadChildren)
    }

    fun closeNode(element: Tree.Element.Node<*>): Boolean {
        val indexInFlattenTree = flattenedTree.indexOf(element)
        if (indexInFlattenTree < 0) return false
        return doCloseNode(element)
    }

    @Suppress("unused")
    fun closeNode(nodeId: Int): Boolean {
        val indexInFlattenTree = flattenedTree.indexOfFirst { it is Tree.Element.Node && it.id == nodeId }
        if (indexInFlattenTree < 0) return false
        return doCloseNode(flattenedTree[indexInFlattenTree] as Tree.Element.Node<*>)
    }

    private fun doCloseNode(element: Tree.Element.Node<*>, skipTreeRefresh: Boolean = false): Boolean {
        Log.d("request node close")
        val nodeIdPath = element.idPath()
        val nodeWasOpen = openNodesMap.remove(nodeIdPath) != null
        element.close()
        // close all children too
        openNodes.forEach { if (it.key.containsAll(nodeIdPath)) doCloseNode(it.value, true) }
        if (nodeWasOpen && !skipTreeRefresh) {
            refreshFlattenTree()
        }
        return nodeWasOpen
    }

    private fun doOpenNode(element: Tree.Element.Node<*>, reloadChildren: Boolean): Boolean {
        Log.d("request node opening")
        return if (element in flattenedTree) {
            openNodesMap[element.idPath()] = element
            element.open(reloadChildren)
            refreshFlattenTree()
            true
        } else {
            false
        }
    }

    internal fun refreshFlattenTree() {
        Log.d("-----treeRefreshed----")
        flattenedTree = tree.roots.flatMap { flattenTree(it) }
        flattenedTree.forEach {
            Log.d(it.id.toString())
        }
        delegate.updateKeysIndexes()
        Log.d("----treeRefreshedPrinted----")
    }

    private val Tree.Element.Node<*>.isOpen: Boolean
        get() = idPath() in openNodesMap

    private fun flattenTree(element: Tree.Element<*>): MutableList<Tree.Element<*>> {
        val orderedChildren = mutableListOf<Tree.Element<*>>()
        when (element) {
            is Tree.Element.Node<*> -> {
                orderedChildren.add(element)
                if (!element.isOpen) return orderedChildren
                element.children?.forEach { child ->
                    orderedChildren.addAll(flattenTree(child))
                }
            }

            is Tree.Element.Leaf<*> -> {
                orderedChildren.add(element)
            }
        }
        return orderedChildren
    }

    suspend fun selectSingleElement(elementIndex: Int, changeFocus: Boolean = true): Boolean {
        delegate.selectSingleItem(elementIndex, changeFocus)
        return true
    }

    suspend fun addElementsToSelection(indexList: List<Int>, itemToFocus: Int? = indexList.lastOrNull()) =
        delegate.addElementsToSelection(indexList, itemToFocus)

    suspend fun addElementToSelection(elementIndex: Int, changeFocus: Boolean = true) = delegate.addElementToSelection(elementIndex, changeFocus)

    suspend fun toggleElementSelection(flattenIndex: Int) {
        delegate.toggleSelection(flattenIndex)
    }

    suspend fun deselectElement(itemIndex: Int, changeFocus: Boolean = true) {
        delegate.deselectSingleElement(itemIndex, changeFocus)
    }

    suspend fun deselectAll() {
        delegate.deselectAll()
    }

    fun isNodeOpen(element: Tree.Element.Node<*>) = element.isOpen

    fun toggleNode(element: Tree.Element.Node<*>) = if (element.isOpen) closeNode(element) else openNode(element)
}
