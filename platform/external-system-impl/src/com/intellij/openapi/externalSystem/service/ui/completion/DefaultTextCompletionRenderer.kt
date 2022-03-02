// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.*
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionRenderer.Cell
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.*

class DefaultTextCompletionRenderer<T> : TextCompletionRenderer<T> {
  override fun getText(item: T): String {
    return item?.toString() ?: ""
  }

  override fun customizeCellRenderer(editor: TextCompletionField<T>, cell: Cell<T>) {
    cell.component.append(getText(cell.item), editor.text)
  }

  companion object {
    fun SimpleColoredComponent.append(text: @NlsSafe String, matchedText: @NlsSafe String) =
      append(text, REGULAR_ATTRIBUTES, matchedText, REGULAR_MATCHED_ATTRIBUTES)

    fun SimpleColoredComponent.append(
      text: @NlsSafe String,
      textAttributes: SimpleTextAttributes,
      matchedText: @NlsSafe String,
      matchedTextAttributes: SimpleTextAttributes
    ) {
      val fragments = text.split(matchedText)
      for ((i, fragment) in fragments.withIndex()) {
        if (fragment.isNotEmpty()) {
          append(fragment, textAttributes)
        }
        if (i < fragments.lastIndex) {
          append(matchedText, matchedTextAttributes)
        }
      }
    }
  }
}