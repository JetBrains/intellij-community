package org.jetbrains.jewel.foundation.tree

import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState

interface TreeViewOnKeyEvent : SelectableColumnOnKeyEvent {

    /**
     * Select Parent Node
     */
    fun onSelectParent(keys: List<SelectableLazyListKey>, state: SelectableLazyListState)

    /**
     * Select Child Node inherited from Right
     */
    fun onSelectChild(keys: List<SelectableLazyListKey>, state: SelectableLazyListState)
}
