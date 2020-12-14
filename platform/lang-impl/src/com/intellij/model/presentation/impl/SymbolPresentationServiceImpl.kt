// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation.impl

import com.intellij.ide.TypePresentationService
import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.model.Symbol
import com.intellij.model.presentation.PresentableSymbol
import com.intellij.model.presentation.SymbolPresentation
import com.intellij.model.presentation.SymbolPresentationProvider
import com.intellij.model.presentation.SymbolPresentationService
import com.intellij.model.psi.PsiSymbolService
import com.intellij.openapi.util.ClassExtension
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewUtil

class SymbolPresentationServiceImpl : SymbolPresentationService {

  companion object {
    private val ourExtension = ClassExtension<SymbolPresentationProvider>("com.intellij.symbolPresentation")
  }

  override fun getSymbolPresentation(symbol: Symbol): SymbolPresentation {
    for (provider: SymbolPresentationProvider in ourExtension.forKey(symbol.javaClass)) {
      val presentation: SymbolPresentation? = provider.getPresentation(symbol)
      if (presentation != null) {
        return presentation
      }
    }
    if (symbol is PresentableSymbol) {
      return symbol.symbolPresentation
    }
    val element: PsiElement? = PsiSymbolService.getInstance().extractElementFromSymbol(symbol)
    if (element != null) {
      return DefaultSymbolPresentation(
        icon = element.getIcon(0),
        typeString = UsageViewUtil.getType(element),
        shortNameString = UsageViewUtil.getShortName(element),
        longNameString = DescriptiveNameUtil.getDescriptiveName(element)
      )
    }
    else {
      val typePresentationService = TypePresentationService.getService()
      return DefaultSymbolPresentation(
        icon = typePresentationService.getIcon(symbol),
        typeString = typePresentationService.getTypeName(symbol) ?: TypePresentationService.getDefaultTypeName(symbol.javaClass),
        shortNameString = typePresentationService.getObjectName(symbol) ?: "<anonymous>"
      )
    }
  }
}
