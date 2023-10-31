package org.jetbrains.jewel.foundation.lazy

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed

interface SelectableColumnKeybindings {

    val KeyEvent.isContiguousSelectionKeyPressed: Boolean

    val PointerKeyboardModifiers.isContiguousSelectionKeyPressed: Boolean

    val KeyEvent.isMultiSelectionKeyPressed: Boolean

    val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean

    /**
     * Select First Node
     */
    val KeyEvent.isSelectFirstItem: Boolean

    /**
     * Extend Selection to First Node inherited from Move Caret to Text Start with Selection
     */
    val KeyEvent.isExtendSelectionToFirstItem: Boolean

    /**
     * Select Last Node inherited from Move Caret to Text End
     */
    val KeyEvent.isSelectLastItem: Boolean

    /**
     * Extend Selection to Last Node inherited from Move Caret to Text End with Selection
     */
    val KeyEvent.isExtendSelectionToLastItem: Boolean

    /**
     * Select Previous Node inherited from Up
     */
    val KeyEvent.isSelectPreviousItem: Boolean

    /**
     * Extend Selection with Previous Node inherited from Up with Selection
     */
    val KeyEvent.isExtendSelectionWithPreviousItem: Boolean

    /**
     * Select Next Node inherited from Down
     */
    val KeyEvent.isSelectNextItem: Boolean

    /**
     * Extend Selection with Next Node inherited from Down with Selection
     */
    val KeyEvent.isExtendSelectionWithNextItem: Boolean

    /**
     * Scroll Page Up and Select Node inherited from Page Up
     */
    val KeyEvent.isScrollPageUpAndSelectItem: Boolean

    /**
     * Scroll Page Up and Extend Selection inherited from Page Up with Selection
     */
    val KeyEvent.isScrollPageUpAndExtendSelection: Boolean

    /**
     * Scroll Page Down and Select Node inherited from Page Down
     */
    val KeyEvent.isScrollPageDownAndSelectItem: Boolean

    /**
     * Scroll Page Down and Extend Selection inherited from Page Down with Selection
     */
    val KeyEvent.isScrollPageDownAndExtendSelection: Boolean

    /**
     * Edit item
     */
    val KeyEvent.isEdit: Boolean

    /**
     * SelectAll
     */
    val KeyEvent.isSelectAll: Boolean
}

open class DefaultMacOsSelectableColumnKeybindings : DefaultSelectableColumnKeybindings() {

    companion object : DefaultMacOsSelectableColumnKeybindings()

    override val KeyEvent.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed

    override val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed
}

open class DefaultSelectableColumnKeybindings : SelectableColumnKeybindings {

    override val KeyEvent.isContiguousSelectionKeyPressed: Boolean
        get() = isShiftPressed

    override val PointerKeyboardModifiers.isContiguousSelectionKeyPressed: Boolean
        get() = isShiftPressed

    override val KeyEvent.isMultiSelectionKeyPressed: Boolean
        get() = isCtrlPressed

    override val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean
        get() = isCtrlPressed

    companion object : DefaultSelectableColumnKeybindings()

    override val KeyEvent.isSelectFirstItem
        get() = key == Key.Home && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToFirstItem
        get() = key == Key.Home && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectLastItem
        get() = key == Key.MoveEnd && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToLastItem
        get() = key == Key.MoveEnd && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectPreviousItem
        get() = key == Key.DirectionUp && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionWithPreviousItem
        get() = key == Key.DirectionUp && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectNextItem
        get() = key == Key.DirectionDown && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionWithNextItem
        get() = key == Key.DirectionDown && isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageUpAndSelectItem
        get() = key == Key.PageUp && !isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageUpAndExtendSelection
        get() = key == Key.PageUp && isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageDownAndSelectItem
        get() = key == Key.PageDown && !isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageDownAndExtendSelection
        get() = key == Key.PageDown && isContiguousSelectionKeyPressed

    override val KeyEvent.isEdit
        get() = false

    override val KeyEvent.isSelectAll: Boolean
        get() = key == Key.A && isMultiSelectionKeyPressed
}
