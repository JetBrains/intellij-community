// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.find.actions

import com.intellij.find.FindUsagesSettings
import com.intellij.find.actions.SearchOptionsService.SearchVariant
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildUsageViewQuery
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.psi.impl.search.runSearch
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewContentManager
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

fun findUsages(showDialog: Boolean, project: Project, selectedScope: SearchScope, target: SearchTarget) {
  val allOptions = getSearchOptions(SearchVariant.FIND_USAGES, target, selectedScope)
  if (showDialog) {
    val canReuseTab = canReuseTab(project)
    val dialog = UsageOptionsDialog(project, target.displayString, allOptions, target.showScopeChooser(), canReuseTab)
    if (!dialog.showAndGet()) {
      // cancelled
      return
    }
    val dialogResult: AllSearchOptions = dialog.result()
    setSearchOptions(SearchVariant.FIND_USAGES, target, dialogResult)
    findUsages(project, target, dialogResult)
  }
  else {
    findUsages(project, target, allOptions)
  }
}

internal fun findUsages(project: Project, target: SearchTarget, allOptions: AllSearchOptions) {
  val query = buildUsageViewQuery(project, target, allOptions)
  val factory = Factory {
    UsageSearcher {
      runSearch(project, query, it)
    }
  }
  val usageViewPresentation = UsageViewPresentation().apply {
    searchString = target.usageHandler.getSearchString(allOptions)
    scopeText = allOptions.options.searchScope.displayName
    tabText = UsageViewBundle.message("search.title.0.in.1", searchString, scopeText)
    isOpenInNewTab = FindUsagesSettings.getInstance().isShowResultsInSeparateView || !canReuseTab(project)
  }
  UsageViewManager.getInstance(project).searchAndShowUsages(
    arrayOf(SearchTarget2UsageTarget(project, target, allOptions)),
    factory,
    false,
    true,
    usageViewPresentation,
    null
  )
}

private fun canReuseTab(project: Project): Boolean {
  val contentManager = UsageViewContentManager.getInstance(project)
  val selectedContent = contentManager.getSelectedContent(true)
  return if (selectedContent == null) {
    contentManager.reusableContentsCount != 0
  }
  else {
    !selectedContent.isPinned
  }
}

internal val SearchTarget.displayString: String get() = presentation().presentableText

@Nls(capitalization = Nls.Capitalization.Title)
internal fun UsageHandler.getSearchString(allOptions: AllSearchOptions): String {
  return getSearchString(allOptions.options)
}

internal fun SearchTarget.showScopeChooser(): Boolean {
  return maximalSearchScope !is LocalSearchScope
}
