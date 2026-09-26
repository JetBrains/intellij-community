// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
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
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.util.TextOccurrencesUtilBase
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.EmptyQuery
import com.intellij.util.Query
import com.intellij.util.codeInsight.CommentUtilCore

private class PsiUsageWithUsageType(private val psiUsage: PsiUsage, override val usageType: UsageType) : PsiUsage by psiUsage {
  override fun createPointer(): Pointer<out PsiUsageWithUsageType> {
    // Intentional local variable; avoids holding onto a reference to `this` in the pointer
    val type = usageType
    return Pointer.delegatingPointer(psiUsage.createPointer(), { PsiUsageWithUsageType(it, type) })
  }
}

private enum class TextOccurrenceType { COMMENT, STRING, PLAIN_TEXT }

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
  return occurrenceQuery.transforming { occurrence: TextOccurrence ->
    val type = getTextOccurrenceType(occurrence, searchStringLength) ?: return@transforming emptyList()
    if (!comments && type == TextOccurrenceType.COMMENT) return@transforming emptyList()
    if (!strings && type == TextOccurrenceType.STRING) return@transforming emptyList()
    if (!plainText && type == TextOccurrenceType.PLAIN_TEXT) return@transforming emptyList()

    val psiUsage = PsiUsage.textUsage(occurrence.element, TextRange.from(occurrence.offsetInElement, searchStringLength))
    val usageType = when (type) {
      TextOccurrenceType.COMMENT -> UsageType.COMMENT_USAGE
      TextOccurrenceType.STRING -> UsageType.LITERAL_USAGE
      else -> UsageType.UNCLASSIFIED
    }

    listOf(PsiUsageWithUsageType(psiUsage, usageType))
  }
}

private fun getTextOccurrenceType(occurrence: TextOccurrence, searchStringLength: Int): TextOccurrenceType? {
  if (occurrence.element is OuterLanguageElement) {
    return null
  }

  var isComment = false
  var isString = false
  for ((element, offsetInElement) in occurrence.walkUp()) {
    if (hasDeclarationsOrReferences(element, offsetInElement, searchStringLength)) {
      return null
    }
    isComment = isComment || (!isString && CommentUtilCore.isCommentTextElement(element))
    isString = isString || (!isComment && TextOccurrencesUtilBase.isStringLiteralElement(element))
  }

  return when {
    isComment -> TextOccurrenceType.COMMENT
    isString -> TextOccurrenceType.STRING
    else -> TextOccurrenceType.PLAIN_TEXT
  }
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
