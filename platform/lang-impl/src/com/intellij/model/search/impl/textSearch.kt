// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.model.psi.impl.hasDeclarationsInElement
import com.intellij.model.psi.impl.hasReferencesInElement
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchService
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.util.TextOccurrencesUtilBase
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import com.intellij.util.codeInsight.CommentUtilCore

internal fun buildTextQuery(
  project: Project,
  searchString: String,
  searchScope: SearchScope,
  searchContexts: Set<SearchContext>
): Query<out TextOccurrence> {
  require(SearchContext.IN_CODE !in searchContexts)
  require(SearchContext.IN_CODE_HOSTS !in searchContexts)
  if (searchContexts.isEmpty()) {
    return EmptyQuery.getEmptyQuery()
  }
  val comments = SearchContext.IN_COMMENTS in searchContexts
  val strings = SearchContext.IN_STRINGS in searchContexts
  val plainText = SearchContext.IN_PLAIN_TEXT in searchContexts
  return SearchService.getInstance()
    .searchWord(project, searchString)
    .inContexts(searchContexts)
    .inScope(searchScope)
    .buildLeafOccurrenceQuery()
    .filtering {
      isApplicableOccurrence(it, comments, strings, plainText)
    }
}

private fun isApplicableOccurrence(occurrence: TextOccurrence, comments: Boolean, strings: Boolean, plainText: Boolean): Boolean {
  var applicableSearchContext = false
  for ((element, offsetInElement) in walkUp(occurrence.element, occurrence.offsetInElement)) {
    if (hasReferencesInElement(element, offsetInElement) || hasDeclarationsInElement(element, offsetInElement)) {
      return false
    }
    applicableSearchContext = applicableSearchContext || isApplicableSearchContext(element, comments, strings, plainText)
  }
  return applicableSearchContext
}

private fun isApplicableSearchContext(element: PsiElement, comments: Boolean, strings: Boolean, plainText: Boolean): Boolean {
  if (comments && strings && plainText) {
    return true
  }
  val isComment = CommentUtilCore.isCommentTextElement(element)
  if (comments && isComment) {
    return true
  }
  val isString = TextOccurrencesUtilBase.isStringLiteralElement(element)
  if (strings && isString) {
    return true
  }
  return plainText && !isComment && !isString
}
