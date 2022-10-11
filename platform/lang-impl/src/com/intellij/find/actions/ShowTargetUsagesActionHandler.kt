// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.find.actions.SearchOptionsService.SearchVariant
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.find.usages.api.UsageOptions.createOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildUsageViewQuery
import com.intellij.ide.nls.NlsMessages
import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.usages.UsageSearchPresentation
import com.intellij.usages.UsageSearcher

// data class for `copy` method
internal data class ShowTargetUsagesActionHandler<O>(
  private val project: Project,
  private val target: SearchTarget,
  private val usageHandler: UsageHandler<O>,
  private val allOptions: AllSearchOptions<O>,
) : ShowUsagesActionHandler {

  override fun isValid(): Boolean = true

  override fun getPresentation(): UsageSearchPresentation =
    object : UsageSearchPresentation {
      override fun getSearchTargetString(): String = usageHandler.getSearchString(allOptions)
      override fun getOptionsString(): String {
        val optionsList = ArrayList<String>()

        if (allOptions.options.isUsages) optionsList.add(LangBundle.message("target.usages.option"))
        if (allOptions.textSearch == true) optionsList.add(LangBundle.message("target.text.occurrences.option"))
        return StringUtil.capitalize(NlsMessages.formatOrList(optionsList))
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
    val dialogResult = dialog.result()
    setSearchOptions(SearchVariant.SHOW_USAGES, target, dialogResult)
    return copy(allOptions = dialogResult)
  }

  override fun withScope(searchScope: SearchScope): ShowUsagesActionHandler {
    return copy(allOptions = allOptions.copy(options = createOptions(allOptions.options.isUsages, searchScope)))
  }

  override fun findUsages(): Unit = findUsages(project, target, usageHandler, allOptions)

  override fun getSelectedScope(): SearchScope = allOptions.options.searchScope

  override fun getMaximalScope(): SearchScope = target.maximalSearchScope ?: GlobalSearchScope.allScope(project)

  override fun getTargetLanguage(): Language? = null

  override fun getTargetClass(): Class<*> = target::class.java

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
        allOptions = getSearchOptions(SearchVariant.SHOW_USAGES, target, usageHandler, searchScope)
      )
    }
  }
}
