package org.jetbrains.jewel.foundation.tree

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.utils.Log

@Composable
fun rememberTreeState() = remember {
    TreeState(SelectableLazyListState(LazyListState()))
}

class TreeState(
    internal val delegate: SelectableLazyListState,
) {

    internal val allNodes = mutableStateListOf<Pair<Any, Int>>()
    internal val openNodes = mutableStateListOf<Any>()

    fun toggleNode(nodeId: Any) {
        Log.d("toggleNode $nodeId")
        if (nodeId in openNodes) {
            openNodes.remove(nodeId)
        } else {
            openNodes.add(nodeId)
        }
        Log.d("open nodes ${openNodes.map { it.toString() }}")
    }
}
