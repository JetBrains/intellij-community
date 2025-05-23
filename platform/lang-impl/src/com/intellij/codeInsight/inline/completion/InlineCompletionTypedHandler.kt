package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionTemplateListener
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase

internal class InlineCompletionTypedHandler(originalHandler: TypedActionHandler) : TypedActionHandlerBase(originalHandler) {

  /**
   * Executes the original handler and starts a typing session if the editor is in the correct state.
   *
   * The [com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionDocumentListener]
   * listens to document changes and saves all changes related to the typing session.
   *
   * When the session ends, a new inline completion request is created and the completion pipeline starts.
   *
   */
  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val file: PsiFile? = if (project == null) null else PsiUtilBase.getPsiFileInEditor(editor, project)

    if (file == null) {
      myOriginalHandler?.execute(editor, charTyped, dataContext)
      return
    }

    if (editor.getUserData(InlineCompletionTemplateListener.Companion.TEMPLATE_IN_PROGRESS_KEY) != null) {
      myOriginalHandler?.execute(editor, charTyped, dataContext)
      return // ML-1684 Do now show inline completion while refactoring
    }

    if (!EditorModificationUtil.checkModificationAllowed(editor)) {
      return
    }
    val typingSessionTracker = InlineCompletion.getHandlerOrNull(editor)?.typingSessionTracker

    //before typing execution
    typingSessionTracker?.startTypingSession(editor)
    //execution
    myOriginalHandler?.execute(editor, charTyped, dataContext)
    //after typing execution
    typingSessionTracker?.endTypingSession(editor)
  }
}
