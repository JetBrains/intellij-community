// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineCompletionRendererCustomization {
  fun renderBlockInlay(editor: Editor, offset: Int, blocks: List<InlineCompletionRenderTextBlock>): Inlay<InlineCompletionLineRenderer>?

  fun getInlineCompletionLineRenderer(
    editor: Editor,
    initialBlocks: List<InlineCompletionRenderTextBlock>
  ): InlineCompletionLineRenderer

  companion object {
    val EP_NAME: ExtensionPointName<InlineCompletionRendererCustomization> = create("com.intellij.inlineCompletionLineRendererCustomization")

    fun getInlineCompletionLineRenderer(
      editor: Editor,
      initialBlocks: List<InlineCompletionRenderTextBlock>
    ): InlineCompletionLineRenderer {
      val extensionList = EP_NAME.extensionList
      assert(extensionList.size < 2)
      return extensionList.firstOrNull()?.getInlineCompletionLineRenderer(editor, initialBlocks)
             ?: InlineCompletionLineRenderer(editor, initialBlocks)
    }
  }
}