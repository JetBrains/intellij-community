package org.jetbrains.jewel.foundation.tree

import org.jetbrains.jewel.foundation.lazy.SelectableColumnOnKeyEvent

interface TreeViewOnKeyEvent : SelectableColumnOnKeyEvent {

    /**
     * Select Parent Node
     */
    suspend fun onSelectParent(flattenedIndex: Int)

    /**
     * Extend Selection to Parent Node inherited from Left with Selection
     */
    suspend fun onExtendSelectionToParent(flattenedIndex: Int)

    /**
     * Select Child Node inherited from Right
     */
    suspend fun onSelectChild(flattenedIndex: Int)

    /**
     * Extend Selection to Child Node inherited from Right with Selection
     */
    suspend fun onExtendSelectionToChild(flattenedIndex: Int)

    /**
     * Select Next Sibling Node
     */
    suspend fun onSelectNextSibling(flattenedIndex: Int)

    /**
     * Select Previous Sibling Node
     */
    suspend fun onSelectPreviousSibling(flattenedIndex: Int)
}
