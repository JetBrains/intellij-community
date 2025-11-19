// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.selectable

/**
 * Marker interface for events that represent a selection action in a selectable lazy layout.
 *
 * Implementations carry the data needed by a [SelectionManager] to update the selection state
 * (e.g. which item, row, or column was targeted and what modifier keys were held).
 *
 * @see SelectionManager.handleEvent
 */
public interface SelectionEvent
