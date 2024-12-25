package com.intellij.microservices.url.references

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.parents
import com.intellij.util.ThreeState
import com.intellij.xml.psi.codeInsight.XmlAutoPopupEnabler

internal class EnableAutopopupInUrlPathReferences : CompletionConfidence() {
  override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    if (hasUsageUrlPathReferences(contextElement, offset)) return ThreeState.NO
    return super.shouldSkipAutopopup(contextElement, psiFile, offset)
  }
}

internal class SlashTypedHandlerAutoPopup : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (charTyped != '/') return super.checkAutoPopup(charTyped, project, editor, file)
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor, CompletionType.BASIC) { f ->
      val offset = editor.caretModel.offset
      val psiElement = f.findElementAt(offset) ?: return@scheduleAutoPopup false
      hasUsageUrlPathReferences(psiElement, offset)
    }
    return super.checkAutoPopup(charTyped, project, editor, file)
  }
}

internal class UrlReferencesXmlAutoPopupEnabler: XmlAutoPopupEnabler {
  override fun shouldShowPopup(file: PsiFile, offset: Int): Boolean {
    val psiElement = file.findElementAt(offset) ?: return false
    return hasUsageUrlPathReferences(psiElement, offset)
  }
}

private fun hasUsageUrlPathReferences(contextElement: PsiElement, offset: Int): Boolean {
  val referenceHost = getReferenceHost(contextElement) ?: return false
  val references = referenceHost.references

  if (references.any { it is UrlPathReference && it.context.isDeclaration }) {
    // do not complete automatically on server-side
    return false
  }

  for (reference in references) {
    if (reference !is UrlSegmentReference) continue
    if (reference.absoluteRange.containsOffset(offset))
      return true
  }
  return false
}

private fun getReferenceHost(contextElement: PsiElement): PsiElement? =
  contextElement.parents(true).take(3).firstOrNull { it is PsiLanguageInjectionHost }