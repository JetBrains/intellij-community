// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

internal class EvaluatorReference(
  val origin: PsiOrigin,
  val targetElements: Collection<PsiElement>
) : PsiSymbolReference {

  override fun getElement(): PsiElement = origin.elementAtPointer

  override fun getRangeInElement(): TextRange = absoluteRange.shiftLeft(element.textRange.startOffset)

  override fun getAbsoluteRange(): TextRange {
    val absoluteRanges = origin.absoluteRanges
    return absoluteRanges.singleOrNull()
           ?: TextRange.create(absoluteRanges.first().startOffset, absoluteRanges.last().endOffset)
  }

  override fun resolveReference(): Collection<Symbol> {
    return targetElements.map(PsiSymbolService.getInstance()::asSymbol)
  }

  override fun toString(): String = "EvaluatorReference(origin=$origin, targetElements=$targetElements)"
}
