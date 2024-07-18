// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create

interface InlineCompletionRendererCustomization {
  companion object {
    val EP_NAME: ExtensionPointName<InlineCompletionRendererCustomization> = create("com.intellij.inlineCompletionLineRendererCustomization")

    fun getInlineCompletionLineRenderer(
      editor: Editor,
      initialBlocks: List<InlineCompletionRenderTextBlock>,
      isSuffix: Boolean = false
    ): InlineCompletionLineRenderer {
      return EP_NAME.extensionList.firstOrNull()?.getInlineCompletionLineRenderer(editor, initialBlocks, isSuffix)
             ?: InlineCompletionLineRenderer(editor, initialBlocks, isSuffix)
    }

    fun renderBlockInlay(editor: Editor,
                         offset: Int,
                         blocks: List<InlineCompletionRenderTextBlock>): Inlay<InlineCompletionLineRenderer>? {
      val inlayFromProvider = EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
        provider.renderBlockInlay(editor, offset, blocks)
      }
      if (inlayFromProvider != null) {
        return inlayFromProvider
      }

      val inlay = editor.inlayModel.addBlockElement(
        offset,
        true,
        false,
        1,
        getInlineCompletionLineRenderer(editor, blocks, false)
      )

      return inlay
    }
  }

  fun renderBlockInlay(editor: Editor, offset: Int, blocks: List<InlineCompletionRenderTextBlock>): Inlay<InlineCompletionLineRenderer>?

  fun getInlineCompletionLineRenderer(
    editor: Editor,
    initialBlocks: List<InlineCompletionRenderTextBlock>,
    isSuffix: Boolean = false
  ): InlineCompletionLineRenderer
}