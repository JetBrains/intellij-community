// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineCompletionInlayRenderer {
  fun renderInlineInlay(
    editor: Editor,
    offset: Int,
    blocks: List<InlineCompletionRenderTextBlock>
  ): Inlay<out InlineCompletionLineRenderer>? = null

  fun renderBlockInlay(
    editor: Editor,
    offset: Int,
    blocks: List<InlineCompletionRenderTextBlock>
  ): Inlay<out InlineCompletionLineRenderer>? = null

  companion object {
    private val EP_NAME: ExtensionPointName<InlineCompletionInlayRenderer> = ExtensionPointName.create(
      "com.intellij.inlineCompletionLineRendererCustomization"
    )

    internal fun all(): List<InlineCompletionInlayRenderer> {
      // The default renderer must be last
      return EP_NAME.extensionList + DefaultInlineCompletionInlaysRenderer()
    }
  }
}

private class DefaultInlineCompletionInlaysRenderer : InlineCompletionInlayRenderer {
  override fun renderInlineInlay(
    editor: Editor,
    offset: Int,
    blocks: List<InlineCompletionRenderTextBlock>
  ): Inlay<InlineCompletionLineRenderer>? {
    return editor.inlayModel.addInlineElement(
      offset,
      true,
      InlineCompletionLineRenderer(editor, blocks)
    )
  }

  override fun renderBlockInlay(
    editor: Editor,
    offset: Int,
    blocks: List<InlineCompletionRenderTextBlock>
  ): Inlay<InlineCompletionLineRenderer>? {
    return editor.inlayModel.addBlockElement(
      offset,
      true,
      false,
      1,
      InlineCompletionLineRenderer(editor, blocks)
    )
  }
}
