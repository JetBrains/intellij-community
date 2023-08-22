// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference

internal class Psi2SymbolReference(private val psiReference: PsiReference) : PsiSymbolReference {

  override fun getElement(): PsiElement = psiReference.element

  override fun getRangeInElement(): TextRange = psiReference.rangeInElement

  override fun resolveReference(): Collection<Symbol> {
    if (psiReference is PsiPolyVariantReference) {
      return psiReference.multiResolve(false).mapNotNull {
        it.element
      }.map(PsiSymbolService.getInstance()::asSymbol)
    }
    else {
      val resolved: PsiElement = psiReference.resolve() ?: return emptyList()
      return listOf(PsiSymbolService.getInstance().asSymbol(resolved))
    }
  }

  override fun resolvesTo(target: Symbol): Boolean {
    val psi = PsiSymbolService.getInstance().extractElementFromSymbol(target)
    if (psi == null) {
      return super.resolvesTo(target)
    }
    else {
      return psiReference.isReferenceTo(psi)
    }
  }
}
