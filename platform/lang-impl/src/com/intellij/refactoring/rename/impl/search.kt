// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.find.usages.impl.TextUsage
import com.intellij.model.Pointer
import com.intellij.model.psi.impl.allReferencesInElement
import com.intellij.model.psi.impl.hasReferencesInElement
import com.intellij.model.search.SearchContext
import com.intellij.model.search.SearchRequest
import com.intellij.model.search.SearchService
import com.intellij.model.search.TextOccurrence
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.walkUp
import com.intellij.refactoring.rename.api.*
import com.intellij.refactoring.util.TextOccurrencesUtilBase
import com.intellij.util.Query
import com.intellij.util.codeInsight.CommentUtilCore

internal fun buildQuery(project: Project, target: RenameTarget, options: RenameOptions): Query<UsagePointer> {
  return buildInnerQuery(project, target, options).mapping {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    it.createPointer()
  }
}

private fun buildInnerQuery(project: Project, target: RenameTarget, options: RenameOptions): Query<out RenameUsage> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  val queries = ArrayList<Query<out RenameUsage>>()
  queries += searchRenameUsages(project, target, options.searchScope)
  if (options.renameTextOccurrences == true) {
    queries += buildTextResultsQueries(project, target, options.searchScope, ReplaceTextTargetContext.IN_PLAIN_TEXT)
  }
  if (options.renameCommentsStringsOccurrences == true) {
    queries += buildTextResultsQueries(project, target, options.searchScope, ReplaceTextTargetContext.IN_COMMENTS_AND_STRINGS)
  }
  return SearchService.getInstance().merge(queries)
}

private fun searchRenameUsages(project: Project, target: RenameTarget, searchScope: SearchScope): Query<out RenameUsage> {
  return SearchService.getInstance().searchParameters(
    DefaultRenameUsageSearchParameters(project, target, searchScope)
  )
}

private class DefaultRenameUsageSearchParameters(
  private val project: Project,
  target: RenameTarget,
  override val searchScope: SearchScope
) : RenameUsageSearchParameters {
  private val pointer: Pointer<out RenameTarget> = target.createPointer()
  override fun areValid(): Boolean = pointer.dereference() != null
  override fun getProject(): Project = project
  override val target: RenameTarget get() = requireNotNull(pointer.dereference())
}

private fun buildTextResultsQueries(project: Project,
                                    target: RenameTarget,
                                    searchScope: SearchScope,
                                    context: ReplaceTextTargetContext): List<Query<out RenameUsage>> {
  val replaceTextTargets: Collection<ReplaceTextTarget> = target.textTargets(context)
  val result = ArrayList<Query<out RenameUsage>>(replaceTextTargets.size)
  for ((searchRequest: SearchRequest, textReplacement: TextReplacement) in replaceTextTargets) {
    val effectiveSearchScope: SearchScope = searchRequest.searchScope?.let(searchScope::intersectWith) ?: searchScope
    val searchString: String = searchRequest.searchString
    val queryBuilder = SearchService.getInstance()
      .searchWord(project, searchString)
      .inScope(effectiveSearchScope)
      .includeInjections()
    if (context == ReplaceTextTargetContext.IN_PLAIN_TEXT) {
      result += queryBuilder
        .inContexts(SearchContext.IN_PLAIN_TEXT)
        .buildLeafOccurrenceQuery()
        .filtering { !hasReferences(it) }
        .mapToUsages(searchString, textReplacement)
    }
    else {
      result += queryBuilder
        .inContexts(SearchContext.IN_COMMENTS)
        .buildLeafOccurrenceQuery()
        .filtering { inComment(it) && !hasReferences(it) }
        .mapToUsages(searchString, textReplacement)
      result += queryBuilder
        .inContexts(SearchContext.IN_STRINGS)
        .buildLeafOccurrenceQuery()
        .filtering { inString(it) && !hasResolvableReferences(it) }
        .mapToUsages(searchString, textReplacement)
    }
  }
  return result
}

private fun inComment(occurrence: TextOccurrence): Boolean {
  for ((element, _) in walkUp(occurrence)) {
    if (CommentUtilCore.isCommentTextElement(element)) {
      return true
    }
  }
  return false
}

private fun inString(occurrence: TextOccurrence): Boolean {
  for ((element, _) in walkUp(occurrence)) {
    if (TextOccurrencesUtilBase.isStringLiteralElement(element)) {
      return true
    }
  }
  return false
}

private fun hasReferences(occurrence: TextOccurrence): Boolean {
  for ((element, offsetInElement) in walkUp(occurrence)) {
    if (hasReferencesInElement(element, offsetInElement)) {
      return true
    }
  }
  return false
}

private fun hasResolvableReferences(occurrence: TextOccurrence): Boolean {
  for ((element, offsetInElement) in walkUp(occurrence)) {
    for (reference in allReferencesInElement(element, offsetInElement)) {
      if (reference.resolveReference().isNotEmpty()) {
        return true
      }
    }
  }
  return false
}

private fun walkUp(occurrence: TextOccurrence): Iterator<Pair<PsiElement, Int>> {
  return walkUp(occurrence.element, occurrence.offsetInElement)
}

private fun Query<out TextOccurrence>.mapToUsages(
  searchString: String,
  textReplacement: TextReplacement
): Query<out RenameUsage> {
  return mapping { occurrence: TextOccurrence ->
    val rangeInElement = TextRange.from(occurrence.offsetInElement, searchString.length)
    TextRenameUsage(TextUsage(occurrence.element, rangeInElement), textReplacement)
  }
}
