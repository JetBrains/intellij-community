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

/** Default [TreeViewKeybindings]: left/right arrows collapse/expand nodes; F2 edits. */
public open class DefaultTreeViewKeybindings : DefaultSelectableColumnKeybindings(), TreeViewKeybindings {
    /** The default singleton instance. */
    public companion object : DefaultTreeViewKeybindings()

    override val KeyEvent.isSelectParent: Boolean
        get() = key == Key.DirectionLeft && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToParent: Boolean
        get() = key == Key.DirectionLeft && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectChild: Boolean
        get() = key == Key.DirectionRight && !isContiguousSelectionKeyPressed

    override val KeyEvent.isExtendSelectionToChild: Boolean
        get() = key == Key.DirectionRight && isContiguousSelectionKeyPressed

    override val KeyEvent.isSelectNextSibling: Boolean
        get() = false

    override val KeyEvent.isSelectPreviousSibling: Boolean
        get() = false

    /** Whether the event triggers an inline edit action (F2). */
    override val KeyEvent.isEdit: Boolean
        get() = key == Key.F2 && !isContiguousSelectionKeyPressed
}

/** Extends [SelectableColumnKeybindings] with tree-specific navigation: expand/collapse and sibling traversal. */
public interface TreeViewKeybindings : SelectableColumnKeybindings {
    /** Select Parent Node. */
    public val KeyEvent.isSelectParent: Boolean

    /** Extend Selection to Parent Node inherited from Left with Selection. */
    public val KeyEvent.isExtendSelectionToParent: Boolean

    /** Select Child Node inherited from Right. */
    public val KeyEvent.isSelectChild: Boolean

    /** Extend Selection to Child Node inherited from Right with Selection. */
    public val KeyEvent.isExtendSelectionToChild: Boolean

    /** Select Next Sibling Node. */
    public val KeyEvent.isSelectNextSibling: Boolean

    /** Select Previous Sibling Node. */
    public val KeyEvent.isSelectPreviousSibling: Boolean
}

/** Default click modifier handler that uses Ctrl on Windows/Linux and Meta (⌘) on macOS. */
@Suppress("unused")
public val DefaultWindowsTreeViewClickModifierHandler: TreeViewClickModifierHandler
    get() = {
        when {
            hostOs.isWindows || hostOs.isLinux -> isCtrlPressed
            hostOs.isMacOS -> isMetaPressed
            else -> false
        }
    }

/** Function type for determining whether a pointer event's modifier keys constitute a multi-selection click. */
public typealias TreeViewClickModifierHandler = PointerKeyboardModifiers.() -> Boolean

/** [DefaultTreeViewKeybindings] for macOS, overriding the multi-selection modifier to `Meta` (⌘). */
public open class DefaultMacOsTreeColumnKeybindings : DefaultTreeViewKeybindings() {
    /** The default singleton instance for macOS. */
    public companion object : DefaultMacOsTreeColumnKeybindings()

    override val KeyEvent.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed

    override val PointerKeyboardModifiers.isMultiSelectionKeyPressed: Boolean
        get() = isMetaPressed
}
