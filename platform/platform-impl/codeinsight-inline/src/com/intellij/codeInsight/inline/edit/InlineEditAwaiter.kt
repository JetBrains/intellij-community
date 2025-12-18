// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.edit

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to wait for all possible inline edit providers: inline completion and Next Edit.
 *
 * The method returns whether a particular inline edit source shows something or no edits were proposed.
 */
@ApiStatus.Internal
interface InlineEditAwaiter {

  suspend fun awaitInlineEdit(project: Project, editor: Editor): Result

  enum class Result {
    SuggestionProvided,
    NothingProvided,
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<InlineEditAwaiter>("com.intellij.inline.edit.awaiter")

    suspend fun awaitAllInlineEdits(project: Project, editor: Editor): Result {
      val isAnythingProvided = EP_NAME.extensionList.any { it.awaitInlineEdit(project, editor) == Result.SuggestionProvided }
      return if (isAnythingProvided) Result.SuggestionProvided else Result.NothingProvided
    }
  }
}

internal class InlineCompletionAwaiter : InlineEditAwaiter {
  override suspend fun awaitInlineEdit(project: Project, editor: Editor): InlineEditAwaiter.Result {
    InlineCompletion.getHandlerOrNull(editor)?.awaitExecution()
    return withContext(Dispatchers.EDT) {
      val isShowing = InlineCompletionSession.getOrNull(editor)?.context?.isCurrentlyDisplaying() == true
      if (isShowing) InlineEditAwaiter.Result.SuggestionProvided else InlineEditAwaiter.Result.NothingProvided
    }
  }
}
