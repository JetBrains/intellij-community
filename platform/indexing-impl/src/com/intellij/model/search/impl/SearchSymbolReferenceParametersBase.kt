// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope

abstract class SearchSymbolReferenceParametersBase : SearchSymbolReferenceParameters {

  final override fun getEffectiveSearchScope(): SearchScope = myEffectiveScope.value

  private val myEffectiveScope: Lazy<SearchScope> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    if (isIgnoreUseScope) {
      originalSearchScope
    }
    else {
      doGetEffectiveSearchScope()
    }
  }

  private fun doGetEffectiveSearchScope(): SearchScope {
    val target = target as? PsiElement ?: return originalSearchScope
    // todo use scope by Symbol
    return ReadAction.compute<SearchScope, RuntimeException> {
      val useScope = PsiSearchHelper.getInstance(project).getUseScope(target)
      originalSearchScope.intersectWith(useScope)
    }
  }
}
