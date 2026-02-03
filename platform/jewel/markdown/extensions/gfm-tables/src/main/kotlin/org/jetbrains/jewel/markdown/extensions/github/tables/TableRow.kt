// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import org.jetbrains.jewel.markdown.MarkdownBlock

internal data class TableRow(val rowIndex: Int, val cells: List<TableCell>) : MarkdownBlock.CustomBlock
