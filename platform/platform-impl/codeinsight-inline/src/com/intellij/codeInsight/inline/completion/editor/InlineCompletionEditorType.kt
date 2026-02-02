// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Experimental
enum class InlineCompletionEditorType {
  MAIN_EDITOR,
  XDEBUGGER,
  COMMIT_MESSAGES,
  AI_ASSISTANT_CHAT_INPUT,
  TERMINAL,
  UNKNOWN;

  @ApiStatus.Internal
  fun supportsLookupCompletion(): Boolean {
    return when (this) {
      MAIN_EDITOR -> true
      XDEBUGGER -> false // do not contain virtual file to build cache key
      COMMIT_MESSAGES -> true
      AI_ASSISTANT_CHAT_INPUT -> true
      TERMINAL -> false // Could be true if needed in the future
      UNKNOWN -> false // Conservative default
    }
  }

  companion object {
    private val forcedInlineCompletionEditorType: Key<InlineCompletionEditorType> = Key<InlineCompletionEditorType>("ml.completion.forced.editor.type")

    @ApiStatus.Internal
    fun force(editor: Editor, type: InlineCompletionEditorType) {
      editor.putUserData(forcedInlineCompletionEditorType, type)
    }

    @TestOnly
    @ApiStatus.Internal
    suspend fun withForced(editor: Editor, type: InlineCompletionEditorType?, block: suspend () -> Unit) {
      if (type == null) return block()
      val previousType = editor.getUserData(forcedInlineCompletionEditorType)
      editor.putUserData(forcedInlineCompletionEditorType, type)
      try {
        block()
      } finally {
        editor.putUserData(forcedInlineCompletionEditorType, previousType)
      }
    }

    @ApiStatus.Internal
    fun get(editor: Editor): InlineCompletionEditorType {
      editor.getUserData(forcedInlineCompletionEditorType)?.let { return it }
      // Determining the editor type in unit tests is not possible.
      // Therefore, MAIN_EDITOR is used if no other editor type is explicitly forced.
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return MAIN_EDITOR
      }
      if (EditorUtil.isRealFileEditor(editor)) {
        return MAIN_EDITOR
      }
      InlineCompletionEditorTypeResolver.EP_NAME.extensionList.firstNotNullOfOrNull { provider ->
        provider.getCustomEditorType(editor)
      }?.let { return it }
      return UNKNOWN
    }
  }
}

@ApiStatus.Internal
interface InlineCompletionEditorTypeResolver {
  fun getCustomEditorType(editor: Editor): InlineCompletionEditorType?

  companion object {
    internal val EP_NAME: ExtensionPointName<InlineCompletionEditorTypeResolver> = ExtensionPointName("com.intellij.inline.completion.editorTypeResolver")
  }
}