package org.jetbrains.jewel.foundation.lazy.tree

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import org.jetbrains.jewel.foundation.lazy.DefaultSelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.SelectableColumnKeybindings
import org.jetbrains.skiko.hostOs

open class DefaultTreeViewKeybindings : DefaultSelectableColumnKeybindings(), TreeViewKeybindings {

    companion object : DefaultTreeViewKeybindings()

    override val KeyEvent.isSelectParent
        get() = key == Key.DirectionLeft && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToParent
        get() = key == Key.DirectionLeft && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectChild
        get() = key == Key.DirectionRight && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToChild
        get() = key == Key.DirectionRight && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectNextSibling
        get() = false

    override val KeyEvent.isSelectPreviousSibling
        get() = false

    override val KeyEvent.isEdit get() = key == Key.F2 && !isContiguousSelectionKeyPressed
}

interface TreeViewKeybindings : SelectableColumnKeybindings {

    /**
     * Select Parent Node
     */
    val KeyEvent.isSelectParent: Boolean

    /**
     * Extend Selection to Parent Node inherited from Left with Selection
     */
    val KeyEvent.isExtendSelectionToParent: Boolean

    /**
     * Select Child Node inherited from Right
     */
    val KeyEvent.isSelectChild: Boolean

    /**
     * Extend Selection to Child Node inherited from Right with Selection
     */
    val KeyEvent.isExtendSelectionToChild: Boolean

    /**
     * Select Next Sibling Node
     */
    val KeyEvent.isSelectNextSibling: Boolean

    /**
     * Select Previous Sibling Node
     */
    val KeyEvent.isSelectPreviousSibling: Boolean
}

@Suppress("unused")
val DefaultWindowsTreeViewClickModifierHandler: TreeViewClickModifierHandler
    get() = {
        when {
            hostOs.isWindows || hostOs.isLinux -> isCtrlPressed
            hostOs.isMacOS -> isMetaPressed
            else -> false
        }
    }

typealias TreeViewClickModifierHandler = PointerKeyboardModifiers.() -> Boolean

open class DefaultMacOsTreeColumnKeybindings : DefaultTreeViewKeybindings() {
    companion object : DefaultMacOsTreeColumnKeybindings()

    override val KeyEvent.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed

    override val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed
}
