package com.intellij.openapi.editor

import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

class HoverDocPopupLocationProviderImpl : HoverDocPopupLocationProvider {
  override fun getPopupPosition(targetOffset: Int, element: PsiElement?, editor: Editor): VisualPosition {
    var offset = targetOffset
    if (element != null && element.isValid()) {
      offset = getElementStartHostOffset(element)
    }
    return editor.offsetToVisualPosition(offset)
  }

  private fun getElementStartHostOffset(element: PsiElement): Int {
    val offset = element.getTextRange().startOffset
    val project = element.getProject()
    val containingFile = element.getContainingFile()
    if (containingFile != null && InjectedLanguageManager.getInstance(project).isInjectedFragment(containingFile)) {
      val document = PsiDocumentManager.getInstance(project).getDocument(containingFile)
      if (document is DocumentWindow) {
        return document.injectedToHost(offset)
      }
    }
    return offset
  }
}