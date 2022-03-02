// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.openapi.externalSystem.service.ui.completion.DefaultTextCompletionRenderer.Companion.append
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionRenderer.Cell
import com.intellij.ui.SimpleTextAttributes
import javax.swing.SwingConstants

class TextCompletionInfoRenderer : TextCompletionRenderer<TextCompletionInfo> {

  override fun getText(item: TextCompletionInfo): String {
    return item.text
  }

  override fun customizeCellRenderer(editor: TextCompletionField<TextCompletionInfo>, cell: Cell<TextCompletionInfo>) {
    val item = cell.item
    val list = cell.list
    with(cell.component) {
      icon = item.icon
      append(item.text, editor.getTextToComplete())
      val description = item.description
      if (description != null) {
        val descriptionForeground = LookupCellRenderer.getGrayedForeground(cell.isSelected)
        val descriptionAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, descriptionForeground)
        append(" ")
        append(description.trim(), descriptionAttributes)
        val padding = maxOf(preferredSize.width, list.width - (ipad.left + ipad.right))
        appendTextPadding(padding, SwingConstants.RIGHT)
      }
    }
  }
}