package com.intellij.codeInsight.completion.inline

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GrayTextProvider {
  suspend fun getProposals(request: GrayTextRequest): List<GrayTextElement>

  object DUMMY : GrayTextProvider {
    override suspend fun getProposals(request: GrayTextRequest): List<GrayTextElement> = emptyList()
  }
}

@ApiStatus.Internal
data class GrayTextRequest(val file: PsiFile, val event: DocumentEvent, val editor: Editor) {
  val document: Document
    get() = event.document
  val startOffset: Int
    get() = event.offset
  val endOffset: Int
    get() = event.offset + event.newLength

  companion object {
    fun fromDocumentEvent(event: DocumentEvent, editor: Editor): GrayTextRequest? {
      val virtualFile = editor.virtualFile ?: return null
      val project = editor.project ?: return null
      val file = ReadAction.compute<PsiFile, Throwable> { PsiManager.getInstance(project).findFile(virtualFile) }

      return GrayTextRequest(file, event, editor)
    }
  }
}

@ApiStatus.Internal
data class GrayTextElement(val text: String)
