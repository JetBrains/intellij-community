// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi.impl

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.pom.PomDeclarationSearcher
import com.intellij.pom.PomTarget
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList

private class DefaultPsiSymbolDeclarationProvider : PsiSymbolDeclarationProvider {
  override fun getDeclarations(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
    for (searcher in PomDeclarationSearcher.EP_NAME.extensionList) {
      ProgressManager.checkCanceled()
      val result = SmartList<PsiSymbolDeclaration>()
      searcher.findDeclarationsAt(element, offsetInElement, fun(target: PomTarget) {
        ProgressManager.checkCanceled()
        result.add(PsiElement2Declaration.createFromPom(target, element) ?: return)
      })
      if (result.isNotEmpty()) {
        return listOf(result.first())
      }
    }

    return emptyList()
  }
}
