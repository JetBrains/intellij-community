// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.lazy.table.selectable

public enum class TableSelectionUnit {
    /** Selects a single cell. */
    Cell,

    /** Selects a row. */
    Row,

    /** Selects a column. */
    Column,

    /** Selects all cells. */
    All,
}
