// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.selectable

/**
 * Describes how a selection action should be applied, typically derived from the keyboard modifiers
 * held at the time of a pointer event.
 *
 * @see SelectionManager.handleEvent
 */
public enum class SelectionType {
    /** No modifier held. Replaces the current selection with the targeted item. */
    Normal,

    /**
     * Shift held. Extends the selection from the current anchor to the targeted item, selecting all
     * items in between.
     */
    Contiguous,

    /** Ctrl (or Cmd on macOS) held. Toggles the targeted item without affecting other selected items. */
    Multi,
}
