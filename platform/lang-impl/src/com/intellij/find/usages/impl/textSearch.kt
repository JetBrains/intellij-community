// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.find.usages.impl

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.Usage
import com.intellij.model.psi.impl.hasReferencesInElement
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.util.Query
import org.jetbrains.annotations.ApiStatus

internal fun SearchTarget.hasTextSearchStrings(): Boolean = textSearchStrings.isNotEmpty()

internal fun buildTextQuery(project: Project, searchString: String, searchScope: SearchScope): Query<out Usage> {
  val length = searchString.length
  return SearchService.getInstance()
    .searchWord(project, searchString)
    .inContexts(SearchContext.IN_PLAIN_TEXT)
    .inScope(searchScope)
    .buildQuery { _, start, offsetInStart ->
      if (hasReferences(start, offsetInStart)) {
        emptyList()
      }
      else {
        listOf(PlainTextUsage(TextUsage(start, TextRange.from(offsetInStart, length))))
      }
    }
}

private fun hasReferences(start: PsiElement, offsetInStart: Int): Boolean {
  for ((element, offsetInElement) in walkUp(start, offsetInStart)) {
    if (hasReferencesInElement(element, offsetInElement)) {
      return true
    }
  }
  return false
}
