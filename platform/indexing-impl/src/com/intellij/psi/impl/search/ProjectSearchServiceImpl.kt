// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.model.search.ProjectSearchService
import com.intellij.model.search.SearchWordParameters
import com.intellij.openapi.project.Project

class ProjectSearchServiceImpl(private val project: Project) : ProjectSearchService {

  override fun searchWord(word: String): SearchWordParameters.Builder {
    return SearchWordParametersImpl(project, word)
  }
}

