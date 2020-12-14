// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

internal data class DeclaredReferencedData(
  val declaredData: TargetData?,
  val referencedData: TargetData?
)

internal data class SymbolWithProvider(val symbol: Symbol, val navigationProvider: Any?)

internal sealed class TargetData {

  abstract val targets: List<SymbolWithProvider>

  class Declared(val declaration: PsiSymbolDeclaration) : TargetData() {
    override val targets: List<SymbolWithProvider> get() = listOf(SymbolWithProvider(declaration.symbol, null))
  }

  class Referenced(val references: List<PsiSymbolReference>) : TargetData() {

    init {
      require(references.isNotEmpty())
    }

    override val targets: List<SymbolWithProvider>
      get() = references.flatMap { reference ->
        reference.resolveReference().map { SymbolWithProvider(it.target, reference) }
      }
  }

  class Evaluator(val origin: PsiOrigin, val targetElements: List<PsiElement>) : TargetData() {

    init {
      require(targetElements.isNotEmpty())
    }

    override val targets: List<SymbolWithProvider>
      get() = targetElements.map {
        SymbolWithProvider(PsiSymbolService.getInstance().asSymbol(it), (origin as? PsiOrigin.Reference)?.reference)
      }
  }
}

internal sealed class PsiOrigin {
  class Leaf(val leaf: PsiElement) : PsiOrigin()
  class Reference(val reference: PsiReference) : PsiOrigin()
}
