// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Contains positional and identity information about an item currently placed in a lazy table. */
public interface LazyTableItemInfo {
    /** Flat linear index of this cell, computed as `row * totalColumns + column`. */
    public val index: Int

    /**
     * Composite key of this cell, represented as a `Pair<columnKey, rowKey>` where the first
     * element is the column key and the second element is the row key.
     */
    public val key: Any

    /** Zero-based row index of this cell in the table grid. */
    public val row: Int

    /** Zero-based column index of this cell in the table grid. */
    public val column: Int

    /** Position of this cell relative to the start of the lazy table layout, in pixels. */
    public val offset: IntOffset

    /** Width and height of this cell in pixels. */
    public val size: IntSize

    /** The content type of this cell, used to group cells of the same type together. */
    public val contentType: Any?
        get() = null
}
