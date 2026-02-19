// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.codeInsight.navigation.getReferenceRanges
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.ReferenceRange
import org.jetbrains.annotations.ApiStatus.Internal

internal data class DeclaredReferencedData(
  val declaredData: TargetData.Declared?,
  val referencedData: TargetData.Referenced?,
)

@Internal
data class SymbolWithProvider(val symbol: Symbol, val navigationProvider: Any?)

@Internal
sealed class TargetData(val drs: List<DeclarationOrReference>) {

  init {
    require(drs.isNotEmpty())
  }

  abstract val targets: List<SymbolWithProvider>

  class Declared(declarations: List<DeclarationOrReference.Declaration>) : TargetData(declarations) {

    val declarations: List<PsiSymbolDeclaration>
      get() {
        return drs.map {
          (it as DeclarationOrReference.Declaration).declaration
        }
      }

    override val targets: List<SymbolWithProvider>
      get() = declarations.map { declaration ->
        SymbolWithProvider(declaration.symbol, null)
      }
  }

  class Referenced(references: List<DeclarationOrReference.Reference>) : TargetData(references) {

    val references: List<PsiSymbolReference>
      get() {
        return drs.map {
          (it as DeclarationOrReference.Reference).reference
        }
      }

    override val targets: List<SymbolWithProvider>
      get() = references.flatMap { reference ->
        reference.resolveReference().map { SymbolWithProvider(it, provider(reference)) }
      }

    private fun provider(reference: PsiSymbolReference): Any? {
      if (reference is EvaluatorReference) {
        return (reference.origin as? PsiOrigin.Reference)?.reference
      }
      else {
        return reference
      }
    }
  }
}

internal sealed class PsiOrigin {

  abstract val absoluteRanges: List<TextRange>

  abstract val elementAtPointer: PsiElement

  class Leaf(val leaf: PsiElement) : PsiOrigin() {

    override val absoluteRanges: List<TextRange> get() = getReferenceRanges(leaf)

    override val elementAtPointer: PsiElement get() = leaf

    override fun toString(): String = "Leaf($leaf)"
  }

  class Reference(val reference: PsiReference) : PsiOrigin() {

    override val absoluteRanges: List<TextRange> get() = ReferenceRange.getAbsoluteRanges(reference)

    override val elementAtPointer: PsiElement get() = reference.element

    override fun toString(): String = "Reference($reference)"
  }
}
