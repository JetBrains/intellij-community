package org.jetbrains.jewel.foundation.lazy.tree

import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState

/** Extends [SelectableColumnOnKeyEvent] with tree-specific handlers for navigating to parent and child nodes. */
public interface TreeViewOnKeyEvent : SelectableColumnOnKeyEvent {
    /** Select Parent Node. */
    public fun onSelectParent(keys: List<SelectableLazyListKey>, state: SelectableLazyListState)

    /** Select Child Node inherited from Right. */
    public fun onSelectChild(keys: List<SelectableLazyListKey>, state: SelectableLazyListState)
}
