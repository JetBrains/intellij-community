package com.intellij.openapi.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface HoverDocPopupLocationProvider {
  fun getPopupPosition(targetOffset: Int, element: PsiElement?, editor: Editor): VisualPosition

  companion object {
    fun getInstance(): HoverDocPopupLocationProvider =
      ApplicationManager.getApplication().getService(HoverDocPopupLocationProvider::class.java)
  }
}