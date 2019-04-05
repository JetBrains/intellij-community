// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.Symbol
import com.intellij.model.SymbolReference
import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchSymbolReferenceParameters
import com.intellij.model.search.SearchWordParameters
import com.intellij.openapi.project.Project
import com.intellij.util.Query

class SearchServiceImpl : SearchService {

  override fun searchTarget(project: Project, symbol: Symbol): SearchSymbolReferenceParameters.Builder {
    return SearchSymbolReferenceParametersImpl(project, symbol)
  }

  override fun searchTarget(parameters: SearchSymbolReferenceParameters): Query<out SymbolReference> {
    return CompositeQuery(SymbolReferenceSearch.createQuery(parameters), SearchParametersQuery(parameters))
  }

  override fun searchWord(project: Project, word: String): SearchWordParameters.Builder {
    return SearchWordParametersImpl(project, word)
  }
}
