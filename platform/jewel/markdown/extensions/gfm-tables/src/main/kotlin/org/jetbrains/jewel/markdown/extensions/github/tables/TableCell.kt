// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.tables

import androidx.compose.ui.Alignment
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock

internal data class TableCell(
    val rowIndex: Int,
    val columnIndex: Int,
    val content: List<InlineMarkdown>,
    val alignment: Alignment.Horizontal?,
) : MarkdownBlock.CustomBlock
