package org.jetbrains.jewel.foundation.tree

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

    override fun KeyEvent.selectParent() =
        key == Key.DirectionLeft && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.extendSelectionToParent() =
        key == Key.DirectionLeft && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.selectChild() =
        key == Key.DirectionRight && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.extendSelectionToChild() =
        key == Key.DirectionRight && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.selectNextSibling() = null

    override fun KeyEvent.selectPreviousSibling() = null

    override fun KeyEvent.edit() =
        key == Key.F2 && !isKeyboardMultiSelectionKeyPressed
}

interface TreeViewKeybindings : SelectableColumnKeybindings {

    /**
     * Select Parent Node
     */
    fun KeyEvent.selectParent(): Boolean?

    /**
     * Extend Selection to Parent Node inherited from Left with Selection
     */
    fun KeyEvent.extendSelectionToParent(): Boolean?

    /**
     * Select Child Node inherited from Right
     */
    fun KeyEvent.selectChild(): Boolean?

    /**
     * Extend Selection to Child Node inherited from Right with Selection
     */
    fun KeyEvent.extendSelectionToChild(): Boolean?

    /**
     * Select Next Sibling Node
     */
    fun KeyEvent.selectNextSibling(): Boolean?

    /**
     * Select Previous Sibling Node
     */
    fun KeyEvent.selectPreviousSibling(): Boolean?
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

    override val KeyEvent.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed

    override val PointerKeyboardModifiers.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed
}
