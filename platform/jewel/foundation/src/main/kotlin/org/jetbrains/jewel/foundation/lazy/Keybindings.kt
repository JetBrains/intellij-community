package org.jetbrains.jewel.foundation.lazy

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed

interface SelectableColumnKeybindings {

    val KeyEvent.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isShiftPressed

    val PointerKeyboardModifiers.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isShiftPressed

    /**
     * Select First Node
     */
    fun KeyEvent.selectFirstItem(): Boolean?

    /**
     * Extend Selection to First Node inherited from Move Caret to Text Start with Selection
     */
    fun KeyEvent.extendSelectionToFirstItem(): Boolean?

    /**
     * Select Last Node inherited from Move Caret to Text End
     */
    fun KeyEvent.selectLastItem(): Boolean?

    /**
     * Extend Selection to Last Node inherited from Move Caret to Text End with Selection
     */
    fun KeyEvent.extendSelectionToLastItem(): Boolean?

    /**
     * Select Previous Node inherited from Up
     */
    fun KeyEvent.selectPreviousItem(): Boolean?

    /**
     * Extend Selection with Previous Node inherited from Up with Selection
     */
    fun KeyEvent.extendSelectionWithPreviousItem(): Boolean?

    /**
     * Select Next Node inherited from Down
     */
    fun KeyEvent.selectNextItem(): Boolean?

    /**
     * Extend Selection with Next Node inherited from Down with Selection
     */
    fun KeyEvent.extendSelectionWithNextItem(): Boolean?

    /**
     * Scroll Page Up and Select Node inherited from Page Up
     */
    fun KeyEvent.scrollPageUpAndSelectItem(): Boolean?

    /**
     * Scroll Page Up and Extend Selection inherited from Page Up with Selection
     */
    fun KeyEvent.scrollPageUpAndExtendSelection(): Boolean?

    /**
     * Scroll Page Down and Select Node inherited from Page Down
     */
    fun KeyEvent.scrollPageDownAndSelectItem(): Boolean?

    /**
     * Scroll Page Down and Extend Selection inherited from Page Down with Selection
     */
    fun KeyEvent.scrollPageDownAndExtendSelection(): Boolean?

    /**
     * Edit item
     */
    fun KeyEvent.edit(): Boolean?
}

open class DefaultSelectableColumnKeybindings : SelectableColumnKeybindings {
    companion object : DefaultSelectableColumnKeybindings()

    override val KeyEvent.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isShiftPressed

    override val PointerKeyboardModifiers.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isShiftPressed

    override fun KeyEvent.selectFirstItem() =
        key == Key.Home && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.extendSelectionToFirstItem() =
        key == Key.Home && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.selectLastItem() =
        key == Key.MoveEnd && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.extendSelectionToLastItem() =
        key == Key.MoveEnd && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.selectPreviousItem() =
        key == Key.DirectionUp && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.extendSelectionWithPreviousItem() =
        key == Key.DirectionUp && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.selectNextItem() =
        key == Key.DirectionDown && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.extendSelectionWithNextItem() =
        key == Key.DirectionDown && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.scrollPageUpAndSelectItem() =
        key == Key.PageUp && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.scrollPageUpAndExtendSelection() =
        key == Key.PageUp && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.scrollPageDownAndSelectItem() =
        key == Key.PageDown && !isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.scrollPageDownAndExtendSelection() =
        key == Key.PageDown && isKeyboardMultiSelectionKeyPressed

    override fun KeyEvent.edit() = false
}

open class DefaultMacOsSelectableColumnKeybindings : DefaultSelectableColumnKeybindings() {
    companion object : DefaultMacOsSelectableColumnKeybindings()

    override val KeyEvent.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed

    override val PointerKeyboardModifiers.isKeyboardMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed
}
