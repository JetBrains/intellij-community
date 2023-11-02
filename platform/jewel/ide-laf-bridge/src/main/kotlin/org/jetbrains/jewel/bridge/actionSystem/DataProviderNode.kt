package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.modifier.ModifierLocalMap
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf

/**
 * Holder for parent node of current [DataProviderNode]. So, each
 * [DataProviderNode] provides itself and read parent node. It allows
 * building tree of [DataProviderNode] and traverse it later on.
 *
 * @see ModifierLocalModifierNode
 */
private val LocalDataProviderNode = modifierLocalOf<DataProviderNode?> { null }

internal class DataProviderNode(
    var dataProvider: (dataId: String) -> Any?,
) : Modifier.Node(), ModifierLocalModifierNode, FocusEventModifierNode {

    // TODO: should we use state here and in parent with children for thread safety? Will it trigger
    // recompositions?
    var hasFocus = false

    var parent: DataProviderNode? = null

    private val _children = mutableSetOf<DataProviderNode>()
    val children: Set<DataProviderNode> = _children

    override val providedValues: ModifierLocalMap = modifierLocalMapOf(LocalDataProviderNode to this)

    override fun onAttach() {
        val oldParent = parent
        parent = LocalDataProviderNode.current
        if (parent !== oldParent) {
            oldParent?._children?.remove(this)
            parent?._children?.add(this)
        }
    }

    override fun onDetach() {
        parent?._children?.remove(this)
        parent = null
    }

    override fun onFocusEvent(focusState: FocusState) {
        hasFocus = focusState.hasFocus
    }

    public fun updateParent() {
        parent = LocalDataProviderNode.current
    }
}
