// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the action ID for inserting inline completions used in hints/tooltips.
 * This allows us to change those shown actions for specific editors.
 */
@ApiStatus.Internal
interface InlineCompletionInsertActionIdResolver {
  /**
   * The action ID to be used for this editor.
   */
  val actionId: String

  /**
   * Returns whether the given editor should use this action ID.
   */
  fun isApplicable(editor: Editor): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineCompletionInsertActionIdResolver>("com.intellij.inline.completion.insertActionIdResolver")
    fun getFor(editor: Editor): String {
      val insertActionId = EP_NAME.extensionList.firstOrNull { it.isApplicable(editor) }?.actionId
      return insertActionId ?: IdeActions.ACTION_INSERT_INLINE_COMPLETION
    }
  }
}