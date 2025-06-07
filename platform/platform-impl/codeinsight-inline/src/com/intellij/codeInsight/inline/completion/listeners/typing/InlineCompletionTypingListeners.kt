// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.utils.InlineCompletionHandlerUtils.hideInlineCompletion
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingListener
import com.intellij.codeInsight.template.TemplateManagerListener
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiFile
import com.intellij.util.application

internal class InlineCompletionDocumentListener(private val editor: Editor) : BulkAwareDocumentListener {
  override fun documentChangedNonBulk(event: DocumentEvent) {
    if ((ClientEditorManager.Companion.getClientId(editor) ?: ClientId.Companion.localId) != ClientId.Companion.current) {
      hideInlineCompletion(editor, InlineCompletionUsageTracker.ShownEvents.FinishType.DOCUMENT_CHANGED)
      return
    }
    val handler = InlineCompletion.getHandlerOrNull(editor)
    handler?.documentChangesTracker?.onDocumentEvent(event, editor)
  }
}

internal class InlineCompletionTypedHandlerDelegate : TypedHandlerDelegate() {

  override fun beforeClosingParenInserted(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    allowTyping(editor, c.toString())
    return super.beforeClosingParenInserted(c, project, editor, file)
  }

  override fun beforeClosingQuoteInserted(quote: CharSequence, project: Project, editor: Editor, file: PsiFile): Result {
    allowTyping(editor, quote.toString())
    return super.beforeClosingQuoteInserted(quote, project, editor, file)
  }

  private fun allowTyping(editor: Editor, typed: String) {
    val handler = InlineCompletion.getHandlerOrNull(editor)
    if (handler != null) {
      val offset = editor.caretModel.offset
      handler.documentChangesTracker.allowTyping(TypingEvent.PairedEnclosureInsertion(typed, offset))
    }
  }
}

internal class InlineCompletionTypingListener : AnActionListener {

  override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
    val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
    val handler = InlineCompletion.getHandlerOrNull(editor) ?: return

    if (editor.getUserData(InlineCompletionTemplateListener.Companion.TEMPLATE_IN_PROGRESS_KEY) != null) {
      return // ML-1684 Do now show inline completion while refactoring
    }

    val offset = editor.caretModel.offset
    handler.documentChangesTracker.allowTyping(TypingEvent.OneSymbol(c, offset))
  }
}

internal class InlineCompletionTemplateListener : TemplateManagerListener {
  override fun templateStarted(state: TemplateState) {
    state.editor?.let { editor ->
      start(editor)
      state.addTemplateStateListener(TemplateStateListener(editor, ::finish))
    }
  }

  private fun start(editor: Editor) {
    editor.putUserData(TEMPLATE_IN_PROGRESS_KEY, Unit)
  }

  private fun finish(editor: Editor) {
    editor.removeUserData(TEMPLATE_IN_PROGRESS_KEY)
  }

  private class TemplateStateListener(private val editor: Editor, private val finish: (Editor) -> Unit) : TemplateEditingListener {

    override fun templateFinished(template: Template, brokenOff: Boolean) {
      finish(editor)
      if (!brokenOff && template.isSuitableToInvokeEvent()) {
        application.invokeLater {
          if (!editor.isDisposed) {
            InlineCompletion.getHandlerOrNull(editor)?.invokeEvent(InlineCompletionEvent.TemplateInserted(editor))
          }
        }
      }
    }

    override fun templateCancelled(template: Template?) {
      finish(editor)
    }

    override fun currentVariableChanged(templateState: TemplateState, template: Template?, oldIndex: Int, newIndex: Int) = Unit

    override fun waitingForInput(template: Template?) = Unit

    override fun beforeTemplateFinished(state: TemplateState, template: Template?) = Unit

    private fun Template.isSuitableToInvokeEvent(): Boolean {
      // Usually, 'key' is responsible for the name of the live template
      return key != ""
    }
  }

  companion object {
    val TEMPLATE_IN_PROGRESS_KEY = Key.create<Unit>("inline.completion.template.in.progress")
  }
}
