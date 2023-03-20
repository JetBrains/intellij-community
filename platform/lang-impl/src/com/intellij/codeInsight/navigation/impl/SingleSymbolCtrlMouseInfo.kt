// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.BaseCtrlMouseInfo
import com.intellij.codeInsight.navigation.CtrlMouseDocInfo
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

@Deprecated("Unused in v2 implementation")
internal class SingleSymbolCtrlMouseInfo(
  symbol: Symbol,
  private val elementAtOffset: PsiElement,
  textRanges: List<TextRange>,
  private val declared: Boolean,
) : BaseCtrlMouseInfo(textRanges) {

  private val pointer: Pointer<out Symbol> = symbol.createPointer()

  private val symbol: Symbol
    get() = requireNotNull(pointer.dereference()) {
      "Must not be called on invalid info"
    }

  override fun isValid(): Boolean = pointer.dereference() != null && elementAtOffset.isValid

  override fun isNavigatable(): Boolean {
    if (declared) {
      return true
    }
    val psi = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
              ?: return true // non-PSI are always navigatable
    return psi !== elementAtOffset && psi !== elementAtOffset.parent
  }

  override fun getDocInfo(): CtrlMouseDocInfo = docInfo(symbol, elementAtOffset)
}
