package com.intellij.microservices.url.inlay

import com.intellij.lang.Language
import com.intellij.psi.PsiElement

interface UrlPathInlayLanguagesProvider {
  val languages: Collection<Language>

  fun getPotentialElementsWithHintsProviders(element: PsiElement): List<PsiElement> = emptyList()
}