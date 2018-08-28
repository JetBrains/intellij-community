// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.PsiElementNavigationTarget
import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.psi.PsiSymbol
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class NavigationServiceImpl(private val project: Project) : NavigationService {

  override fun getNavigationTargets(reference: SymbolReference): Collection<NavigationTarget> {
    if (reference is PsiReference) {
      // fallback to old API
      return TargetElementUtil.getInstance().getTargetCandidates(reference).map(::PsiElementNavigationTarget)
    }
    val results = reference.resolve(false)
    val symbols = results.map { it.target }
    return symbols.flatMap {
      it.getNavigationTargets(project)
    }
  }

  override fun getNavigationTargets(symbol: Symbol): Collection<NavigationTarget> {
    return when (symbol) {
      is NavigationTarget -> listOf(symbol)
      is PsiElement -> listOf(PsiElementNavigationTarget(symbol))
      is PsiSymbol -> listOf(PsiElementNavigationTarget(symbol.element))
      else -> emptyList()
    }
  }
}
