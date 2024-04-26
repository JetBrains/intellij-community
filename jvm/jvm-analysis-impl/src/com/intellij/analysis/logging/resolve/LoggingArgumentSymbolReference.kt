// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.logging.resolve

import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class LoggingArgumentSymbolReference(
  private val literalExpression: PsiElement,
  private val literalRange: TextRange,
  private val externalReference: PsiElement,
) : PsiSymbolReference {
  override fun getElement(): PsiElement = literalExpression

  override fun getRangeInElement(): TextRange = literalRange

  override fun resolveReference(): Collection<Symbol> = listOf(LoggingArgumentSymbol(externalReference))
}