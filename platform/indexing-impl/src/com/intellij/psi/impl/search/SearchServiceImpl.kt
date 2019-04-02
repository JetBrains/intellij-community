// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.SearchService
import com.intellij.model.search.SearchWordParameters
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.project.Project
import com.intellij.util.Query

class SearchServiceImpl(private val project: Project) : SearchService {

  override fun searchWord(word: String): SearchWordParameters {
    return SearchWordParametersImpl(project, word)
  }

  override fun searchWord(parameters: SearchWordParameters): Query<out TextOccurrence> {
    return SearchWordQueryImpl(project, parameters)
  }
}
