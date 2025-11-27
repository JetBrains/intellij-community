// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.edit

import com.intellij.codeInsight.hint.HintManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Awaits all possible inline edit providers (completion and Next Edit). If nothing is shown after they finish,
 * the 'No suggestions' hint is shown.
 *
 * This service is used only after a user requests inline completion explicitly by shortcut.
 */
@Service(Service.Level.PROJECT)
internal class NoInlineEditShownNotifier(private val project: Project, scope: CoroutineScope) : Disposable {

  private val hintRequestExecutor = InlineEditRequestExecutor.create(scope)

  @RequiresEdt
  fun notifyNoSuggestionIfNothingIsShown(editor: Editor) {
    val initialEditorState = runReadAction { editor.getState() }
    hintRequestExecutor.switchRequest(onJobCreated = {}) {
      val result = InlineEditAwaiter.awaitAllInlineEdits(project, editor)
      when (result) {
        InlineEditAwaiter.Result.SuggestionProvided -> Unit
        InlineEditAwaiter.Result.NothingProvided -> {
          withContext(Dispatchers.EDT) {
            val finalEditorState = runReadAction { editor.getState() }
            if (initialEditorState == finalEditorState) {
              coroutineToIndicator {
                HintManager.getInstance().showInformationHint(editor, LangBundle.message("completion.no.suggestions"), HintManager.ABOVE)
              }
            }
          }
        }
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(hintRequestExecutor)
  }

  private fun Editor.getState(): EditorState {
    return EditorState(document.modificationStamp, caretModel.offset)
  }

  private data class EditorState(val modificationStamp: Long, val caretOffset: Int)

  companion object {
    fun getInstance(project: Project): NoInlineEditShownNotifier = project.service()
  }
}
