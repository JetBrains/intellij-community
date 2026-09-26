package org.jetbrains.jewel.foundation.actionSystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.node.TraversableNode
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * A [Modifier.Node] that tracks focus and exposes data to the IntelliJ Platform action system via a
 * [DataProviderContext] lambda.
 */
@InternalJewelApi
@ApiStatus.Internal
public class DataProviderNode(public var dataProvider: DataProviderContext.() -> Unit) :
    Modifier.Node(), FocusEventModifierNode, TraversableNode {
    public var hasFocus: Boolean = false

    override fun onFocusEvent(focusState: FocusState) {
        hasFocus = focusState.hasFocus
    }

    override val traverseKey: TraverseKey = TraverseKey

    /** The traversal key used to locate [DataProviderNode] instances in the Modifier node tree. */
    public companion object TraverseKey
}
