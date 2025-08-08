// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import org.jetbrains.jewel.markdown.MarkdownBlock

internal data class TableBlock(val header: TableHeader, val rows: List<TableRow>) : MarkdownBlock.CustomBlock {
    val rowCount: Int = rows.size + 1 // We always have a header
    val columnCount: Int

    init {
        require(header.cells.isNotEmpty()) { "Header cannot be empty" }
        val headerColumns = header.cells.size

        if (rows.isNotEmpty()) {
            val bodyColumns = rows.first().cells.size
            require(rows.all { it.cells.size == bodyColumns }) { "Inconsistent cell count in table body" }
            require(headerColumns == bodyColumns) { "Inconsistent cell count between table body and header" }
        }

        columnCount = headerColumns
    }
}
