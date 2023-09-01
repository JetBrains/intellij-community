package org.jetbrains.jewel.foundation.tree

import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent

interface TreeViewOnKeyEvent : SelectableColumnOnKeyEvent {

    /**
     * Select Parent Node
     */
    suspend fun onSelectParent(flattenedIndex: Int)

    /**
     * Select Child Node inherited from Right
     */
    suspend fun onSelectChild(flattenedIndex: Int)
}
