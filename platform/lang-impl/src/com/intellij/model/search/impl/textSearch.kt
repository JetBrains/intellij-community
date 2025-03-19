// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.psi.impl.hasDeclarationsInElement
import com.intellij.model.psi.impl.hasReferencesInElement
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchRequest
import com.intellij.model.search.SearchService
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.util.TextOccurrencesUtilBase
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import com.intellij.util.codeInsight.CommentUtilCore

internal fun buildTextUsageQuery(
  project: Project,
  searchRequest: SearchRequest,
  searchScope: SearchScope,
  searchContexts: Set<SearchContext>
): Query<out PsiUsage> {
  require(SearchContext.inCode() !in searchContexts)
  require(SearchContext.inCodeHosts() !in searchContexts)
  if (searchContexts.isEmpty()) {
    return EmptyQuery.getEmptyQuery()
  }
  val searchString = searchRequest.searchString
  val searchStringLength = searchString.length
  val effectiveSearchScope = searchRequest.searchScope?.let(searchScope::intersectWith)
                             ?: searchScope
  val comments = SearchContext.inComments() in searchContexts
  val strings = SearchContext.inStrings() in searchContexts
  val plainText = SearchContext.inPlainText() in searchContexts
  val occurrenceQuery = SearchService.getInstance()
    .searchWord(project, searchString)
    .inContexts(searchContexts)
    .inScope(effectiveSearchScope)
    .buildLeafOccurrenceQuery()
  val filteredOccurrenceQuery = if (comments && strings && plainText) {
    occurrenceQuery.filtering {
      isApplicableOccurrence(it, searchStringLength)
    }
  }
  else {
    occurrenceQuery.filtering {
      isApplicableOccurrence(it, searchStringLength, comments, strings, plainText)
    }
  }
  return filteredOccurrenceQuery.mapping { occurrence: TextOccurrence ->
    PsiUsage.textUsage(occurrence.element, TextRange.from(occurrence.offsetInElement, searchStringLength))
  }
}

private fun isApplicableOccurrence(occurrence: TextOccurrence, searchStringLength: Int): Boolean {
  for ((element, offsetInElement) in occurrence.walkUp()) {
    if (hasDeclarationsOrReferences(element, offsetInElement, searchStringLength)) {
      return false
    }
  }
  return true
}

private fun isApplicableOccurrence(
  occurrence: TextOccurrence,
  searchStringLength: Int,
  comments: Boolean,
  strings: Boolean,
  plainText: Boolean
): Boolean {
  var isComment = false
  var isString = false
  for ((element, offsetInElement) in occurrence.walkUp()) {
    if (hasDeclarationsOrReferences(element, offsetInElement, searchStringLength)) {
      return false
    }
    isComment = isComment || CommentUtilCore.isCommentTextElement(element)
    isString = isString || TextOccurrencesUtilBase.isStringLiteralElement(element)
  }
  return comments && isComment ||
         strings && isString ||
         plainText && !isComment && !isString
}

private fun TextOccurrence.walkUp(): Iterator<Pair<PsiElement, Int>> = walkUp(element, offsetInElement)

private fun hasDeclarationsOrReferences(
  element: PsiElement,
  startOffsetInElement: Int,
  searchStringLength: Int
): Boolean {
  val endOffsetInElement = startOffsetInElement + searchStringLength
  return hasDeclarationsInElement(element, startOffsetInElement, endOffsetInElement) ||
         hasReferencesInElement(element, startOffsetInElement, endOffsetInElement)
}
