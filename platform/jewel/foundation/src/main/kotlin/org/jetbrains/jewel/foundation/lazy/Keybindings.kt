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

public interface SelectableColumnKeybindings {
    public val KeyEvent.isContiguousSelectionKeyPressed: Boolean

    public val PointerKeyboardModifiers.isContiguousSelectionKeyPressed: Boolean

    public val KeyEvent.isMultiSelectionKeyPressed: Boolean

    public val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean

    /** Select First Node. */
    public val KeyEvent.isSelectFirstItem: Boolean

    /** Extend Selection to First Node inherited from Move Caret to Text Start with Selection. */
    public val KeyEvent.isExtendSelectionToFirstItem: Boolean

    /** Select Last Node inherited from Move Caret to Text End. */
    public val KeyEvent.isSelectLastItem: Boolean

    /** Extend Selection to Last Node inherited from Move Caret to Text End with Selection. */
    public val KeyEvent.isExtendSelectionToLastItem: Boolean

    /** Select Previous Node inherited from Up. */
    public val KeyEvent.isSelectPreviousItem: Boolean

    /** Extend Selection with Previous Node inherited from Up with Selection. */
    public val KeyEvent.isExtendSelectionWithPreviousItem: Boolean

    /** Select Next Node inherited from Down. */
    public val KeyEvent.isSelectNextItem: Boolean

    /** Extend Selection with Next Node inherited from Down with Selection. */
    public val KeyEvent.isExtendSelectionWithNextItem: Boolean

    /** Scroll Page Up and Select Node inherited from Page Up. */
    public val KeyEvent.isScrollPageUpAndSelectItem: Boolean

    /** Scroll Page Up and Extend Selection inherited from Page Up with Selection. */
    public val KeyEvent.isScrollPageUpAndExtendSelection: Boolean

    /** Scroll Page Down and Select Node inherited from Page Down. */
    public val KeyEvent.isScrollPageDownAndSelectItem: Boolean

    /** Scroll Page Down and Extend Selection inherited from Page Down with Selection. */
    public val KeyEvent.isScrollPageDownAndExtendSelection: Boolean

    /** Edit item. */
    public val KeyEvent.isEdit: Boolean

    /** Select all items. */
    public val KeyEvent.isSelectAll: Boolean
}

public open class DefaultMacOsSelectableColumnKeybindings : DefaultSelectableColumnKeybindings() {
    public companion object : DefaultMacOsSelectableColumnKeybindings()

    override val KeyEvent.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed

    override val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed
}

public open class DefaultSelectableColumnKeybindings : SelectableColumnKeybindings {
    public companion object : DefaultSelectableColumnKeybindings()

    override val KeyEvent.isContiguousSelectionKeyPressed: Boolean
        get() = isShiftPressed

    override val PointerKeyboardModifiers.isContiguousSelectionKeyPressed: Boolean
        get() = isShiftPressed

    override val KeyEvent.isMultiSelectionKeyPressed: Boolean
        get() = isCtrlPressed

    override val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean
        get() = isCtrlPressed

    override val KeyEvent.isSelectFirstItem: Boolean
        get() = key == Key.Home && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToFirstItem: Boolean
        get() = key == Key.Home && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectLastItem: Boolean
        get() = key == Key.MoveEnd && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToLastItem: Boolean
        get() = key == Key.MoveEnd && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectPreviousItem: Boolean
        get() = key == Key.DirectionUp && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionWithPreviousItem: Boolean
        get() = key == Key.DirectionUp && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectNextItem: Boolean
        get() = key == Key.DirectionDown && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionWithNextItem: Boolean
        get() = key == Key.DirectionDown && isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageUpAndSelectItem: Boolean
        get() = key == Key.PageUp && !isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageUpAndExtendSelection: Boolean
        get() = key == Key.PageUp && isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageDownAndSelectItem: Boolean
        get() = key == Key.PageDown && !isContiguousSelectionKeyPressed

    override val KeyEvent.isScrollPageDownAndExtendSelection: Boolean
        get() = key == Key.PageDown && isContiguousSelectionKeyPressed

    override val KeyEvent.isEdit: Boolean
        get() = false

    override val KeyEvent.isSelectAll: Boolean
        get() = key == Key.A && isMultiSelectionKeyPressed
}
