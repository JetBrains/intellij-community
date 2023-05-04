package com.intellij.codeInsight.completion.inline

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineCompletionProvider {
  suspend fun getProposals(request: InlineCompletionRequest): List<InlineCompletionProposal>

  object DUMMY : InlineCompletionProvider {
    override suspend fun getProposals(request: InlineCompletionRequest): List<InlineCompletionProposal> = emptyList()
  }
}

@ApiStatus.Internal
data class InlineCompletionRequest(val file: PsiFile, val event: DocumentEvent, val editor: Editor) {
  val document: Document
    get() = event.document
  val startOffset: Int
    get() = event.offset
  val endOffset: Int
    get() = event.offset + event.newLength

  companion object {
    fun fromDocumentEvent(event: DocumentEvent, editor: Editor): InlineCompletionRequest? {
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      val file = ReadAction.compute<PsiFile, Throwable> { PsiManager.getInstance(project).findFile(virtualFile) }

      return InlineCompletionRequest(file, event, editor)
    }
  }
}

@ApiStatus.Internal
data class InlineCompletionProposal(val text: String)
