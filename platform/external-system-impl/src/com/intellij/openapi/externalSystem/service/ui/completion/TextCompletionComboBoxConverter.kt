// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.completion

interface TextCompletionComboBoxConverter<T> : TextCompletionRenderer<T> {

  fun getItem(text: String): T

  class Default : TextCompletionComboBoxConverter<String> {

    private val renderer = TextCompletionRenderer.Default<String>()

    override fun getItem(text: String): String = text

    override fun getText(item: String): String = renderer.getText(item)

    override fun customizeCellRenderer(
      editor: TextCompletionField<String>,
      cell: TextCompletionRenderer.Cell<String>
    ) = renderer.customizeCellRenderer(editor, cell)
  }
}