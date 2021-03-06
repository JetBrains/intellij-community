// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Pointer
import com.intellij.model.search.SearchRequest
import com.intellij.model.search.SearchService
import com.intellij.model.search.impl.buildTextUsageQuery
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.api.*
import com.intellij.util.Query
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun buildQuery(project: Project, target: RenameTarget, options: RenameOptions): Query<UsagePointer> {
  return buildUsageQuery(project, target, options).mapping {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    it.createPointer()
  }
}

internal fun buildUsageQuery(project: Project, target: RenameTarget, options: RenameOptions): Query<out RenameUsage> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  val queries = ArrayList<Query<out RenameUsage>>()
  queries += searchRenameUsages(project, target, options.searchScope)
  if (options.textOptions.commentStringOccurrences == true) {
    queries += buildTextResultsQueries(project, target, options.searchScope, ReplaceTextTargetContext.IN_COMMENTS_AND_STRINGS)
  }
  if (options.textOptions.textOccurrences == true) {
    queries += buildTextResultsQueries(project, target, options.searchScope, ReplaceTextTargetContext.IN_PLAIN_TEXT)
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
  for ((searchRequest: SearchRequest, usageTextByName: UsageTextByName) in replaceTextTargets) {
    val effectiveSearchScope: SearchScope = searchRequest.searchScope?.let(searchScope::intersectWith) ?: searchScope
    val fileUpdater = fileRangeUpdater(usageTextByName)
    result += buildTextUsageQuery(project, searchRequest, effectiveSearchScope, context.searchContexts)
      .mapping {
        TextRenameUsage(it, fileUpdater, context)
      }
  }
  return result
}
