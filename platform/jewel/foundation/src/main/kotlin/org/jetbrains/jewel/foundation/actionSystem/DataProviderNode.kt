package org.jetbrains.jewel.foundation.actionSystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.TraversableNode

public class DataProviderNode(public var dataProvider: DataProviderContext.() -> Unit) :
    Modifier.Node(), FocusEventModifierNode, TraversableNode {
    public var hasFocus: Boolean = false

    override fun onFocusEvent(focusState: FocusState) {
        hasFocus = focusState.hasFocus
    }

    override val traverseKey: TraverseKey = TraverseKey

    public companion object TraverseKey
}
