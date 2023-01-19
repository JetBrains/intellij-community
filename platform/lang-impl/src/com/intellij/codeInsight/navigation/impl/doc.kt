// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.CtrlMouseDocInfo
import com.intellij.codeInsight.navigation.SingleTargetElementInfo
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTarget
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolService
import com.intellij.psi.PsiElement

@Suppress("DEPRECATION")
@Deprecated("Unused in v2 implementation")
internal fun docInfo(symbol: Symbol, elementAtPointer: PsiElement): CtrlMouseDocInfo {
  fromPsi(symbol, elementAtPointer)?.let {
    return it
  }
  val target = symbolDocumentationTarget(elementAtPointer.project, symbol)
  if (target == null) {
    return CtrlMouseDocInfo.EMPTY
  }
  return CtrlMouseDocInfo(target.computeDocumentationHint(), null, null)
}

@Suppress("DEPRECATION")
@Deprecated("Unused in v2 implementation")
private fun fromPsi(symbol: Symbol, elementAtPointer: PsiElement): CtrlMouseDocInfo? {
  val psi = PsiSymbolService.getInstance().extractElementFromSymbol(symbol) ?: return null
  val info = SingleTargetElementInfo.generateInfo(psi, elementAtPointer, true)
  return info.takeUnless {
    it === CtrlMouseDocInfo.EMPTY
  }
}
