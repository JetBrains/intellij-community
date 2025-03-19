package com.intellij.openapi.editor

import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HoverDocPopupLocationProviderImpl : HoverDocPopupLocationProvider {
  override fun getPopupPosition(targetOffset: Int, element: PsiElement?, editor: Editor): VisualPosition {
    val targetPosition = editor.offsetToVisualPosition(targetOffset)
    if (element != null && element.isValid()) {
      val elementOffset = getElementStartHostOffset(element)
      if (elementOffset != targetOffset) {
        val elementPosition = editor.offsetToVisualPosition(elementOffset)
        // In the case of multiline PsiElement, the elementPosition might be not on the same line where the targetPosition is located.
        // Showing a popup far from the current line looks not great
        // (the targetPosition, and the current line are very likely to get covered by the popup).
        // So, it's ok to use the elementPosition only if it's on the same line as the targetPosition.
        if (elementPosition.line == targetPosition.line) {
          return elementPosition
        }
      }
    }
    return targetPosition
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