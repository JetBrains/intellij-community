// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.notebooks.visualization.context.NotebookDataContext.selectedCellInterval
import com.intellij.openapi.actionSystem.DataContext
import org.intellij.plugins.markdown.ui.actions.MarkdownActionPromoterExtension

internal class NotebookMarkdownCellActionPromoterExtension : MarkdownActionPromoterExtension {
  override fun shouldPromoteMarkdownActions(context: DataContext): Boolean {
    return context.selectedCellInterval?.type == CellType.MARKDOWN
  }
}