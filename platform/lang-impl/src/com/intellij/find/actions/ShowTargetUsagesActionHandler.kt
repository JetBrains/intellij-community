// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.api.UsageOptions.createOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildUsageViewQuery
import com.intellij.find.usages.impl.hasTextSearchStrings
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.usages.UsageSearchPresentation
import com.intellij.usages.UsageSearcher

// data class for `copy` method
internal data class ShowTargetUsagesActionHandler<O>(
  private val project: Project,
  private val target: SearchTarget,
  private val usageHandler: UsageHandler<O>,
  private val allOptions: AllSearchOptions<O>
) : ShowUsagesActionHandler {

  override fun isValid(): Boolean = true

  override fun getPresentation(): UsageSearchPresentation {
    return UsageSearchPresentation {
      usageHandler.getSearchString(allOptions)
    }
  }

  override fun createUsageSearcher(): UsageSearcher {
    val query = buildUsageViewQuery(project, target, usageHandler, allOptions)
    return UsageSearcher {
      query.forEach(it)
    }
  }

  override fun showDialog(): ShowUsagesActionHandler? {
    val dialog = UsageOptionsDialog(project, target.displayString, usageHandler, allOptions, target.showScopeChooser(), false)
    if (!dialog.showAndGet()) {
      // cancelled
      return null
    }
    else {
      return copy(allOptions = dialog.result())
    }
  }

  override fun withScope(searchScope: SearchScope): ShowUsagesActionHandler {
    return copy(allOptions = allOptions.copy(options = createOptions(allOptions.options.isUsages, searchScope)))
  }

  override fun findUsages(): Unit = findUsages(project, target, usageHandler, allOptions)

  override fun getSelectedScope(): SearchScope = allOptions.options.searchScope

  override fun getMaximalScope(): SearchScope = target.maximalSearchScope ?: GlobalSearchScope.allScope(project)

  companion object {

    @JvmStatic
    fun showUsages(project: Project, searchScope: SearchScope, target: SearchTarget, parameters: ShowUsagesParameters) {
      ShowUsagesAction.showElementUsages(parameters, createActionHandler(project, searchScope, target, target.usageHandler))
    }

    private fun <O> createActionHandler(project: Project,
                                        searchScope: SearchScope,
                                        target: SearchTarget,
                                        usageHandler: UsageHandler<O>): ShowTargetUsagesActionHandler<O> {
      return ShowTargetUsagesActionHandler(
        project,
        target = target,
        usageHandler = usageHandler,
        allOptions = AllSearchOptions(
          options = createOptions(searchScope),
          textSearch = if (target.hasTextSearchStrings()) false else null,
          customOptions = usageHandler.getCustomOptions(UsageHandler.UsageAction.SHOW_USAGES)
        )
      )
    }
  }
}
