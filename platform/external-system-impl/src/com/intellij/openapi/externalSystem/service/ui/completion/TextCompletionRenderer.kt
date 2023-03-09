// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.codeInsight.lookup.impl.LookupCellRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

interface TextCompletionRenderer<T> {

  fun getText(item: T): String

  fun customizeCellRenderer(editor: TextCompletionField<T>, cell: Cell<T>)

  data class Cell<T>(
    val component: SimpleColoredComponent,
    val item: T,
    val list: JList<*>,
    val index: Int,
    val isSelected: Boolean,
    val hasFocus: Boolean
  )

  class Default<T> : TextCompletionRenderer<T> {

    override fun getText(item: T): String {
      return item?.toString() ?: ""
    }

    override fun customizeCellRenderer(editor: TextCompletionField<T>, cell: Cell<T>) {
      cell.component.append(getText(cell.item), editor.text)
    }
  }

  companion object {
    fun SimpleColoredComponent.append(text: @NlsSafe String, matchedText: @NlsSafe String) =
      append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES, matchedText, LookupCellRenderer.REGULAR_MATCHED_ATTRIBUTES)

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