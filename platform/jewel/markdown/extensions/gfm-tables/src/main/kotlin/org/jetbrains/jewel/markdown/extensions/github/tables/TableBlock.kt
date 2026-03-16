// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.MarkdownBlock

@ApiStatus.Experimental
@ExperimentalJewelApi
public data class TableBlock(val header: TableHeader?, val rows: List<TableRow>) : MarkdownBlock.CustomBlock {
    val rowCount: Int = rows.size + if (header != null) 1 else 0
    val columnCount: Int

    init {
        if (header != null) {
            require(header.cells.isNotEmpty()) { "Header cannot be empty" }
        }
        require(header != null || rows.isNotEmpty()) { "Table must have a header or at least one row" }

        val headerColumns = header?.cells?.size
        val bodyColumns = rows.firstOrNull()?.cells?.size

        if (headerColumns != null && bodyColumns != null) {
            require(headerColumns == bodyColumns) { "Inconsistent cell count between table body and header" }
        }
        if (rows.isNotEmpty()) {
            val firstRowSize = rows.first().cells.size
            require(rows.all { it.cells.size == firstRowSize }) { "Inconsistent cell count in table body" }
        }

        columnCount = headerColumns ?: bodyColumns ?: error("Table must have at least one row or header")
    }
}
