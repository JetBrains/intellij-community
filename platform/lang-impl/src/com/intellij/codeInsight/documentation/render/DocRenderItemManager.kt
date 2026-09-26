// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DocRenderItemManager {
  companion object {
    @JvmStatic
    fun getInstance(): DocRenderItemManager = service()
  }

  fun getItemAroundOffset(editor: Editor, offset: Int): DocRenderItem? {
    return null
  }

  fun removeAllItems(editor: Editor)

  fun setItemsToEditor(editor: Editor, itemsToSet: DocRenderPassFactory.Items, collapseNewItems: Boolean)

  fun resetToDefaultState(editor: Editor)

  fun getItems(editor: Editor): Collection<DocRenderItem>?

  fun isRenderedDocHighlighter(highlighter: RangeHighlighter): Boolean
}

