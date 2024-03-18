// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.find.actions.SearchOptionsService.SearchVariant
import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageOptions.createOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.find.usages.impl.buildUsageViewQuery
import com.intellij.ide.nls.NlsMessages
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageSearchPresentation
import com.intellij.usages.UsageSearcher

// data class for `copy` method
internal data class ShowTargetUsagesActionHandler(
  private val project: Project,
  private val target: SearchTarget,
  private val allOptions: AllSearchOptions
) : ShowUsagesActionHandler {

  override fun isValid(): Boolean = true

  override fun getPresentation(): UsageSearchPresentation =
    object : UsageSearchPresentation {
      override fun getSearchTargetString(): String = target.usageHandler.getSearchString(allOptions)
      override fun getOptionsString(): String {
        val optionsList = ArrayList<String>()

        if (allOptions.options.isUsages) optionsList.add(LangBundle.message("target.usages.option"))
        if (allOptions.textSearch == true) optionsList.add(LangBundle.message("target.text.occurrences.option"))
        return StringUtil.capitalize(NlsMessages.formatOrList(optionsList))
      }
    }

  override fun createUsageSearcher(): UsageSearcher {
    val query = buildUsageViewQuery(project, target, allOptions)
    return UsageSearcher {
      query.forEach(it)
    }
  }

  override fun showDialog(): ShowUsagesActionHandler? {
    val dialog = UsageOptionsDialog(project, target.displayString, allOptions, target.showScopeChooser(), false)
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

  override fun findUsages(): Unit = findUsages(project, target, allOptions)

  override fun moreUsages(parameters: ShowUsagesParameters) = parameters.moreUsages()

  override fun getSelectedScope(): SearchScope = allOptions.options.searchScope

  override fun getMaximalScope(): SearchScope = target.maximalSearchScope ?: GlobalSearchScope.allScope(project)

  override fun getTargetLanguage(): Language? = null

  override fun getTargetClass(): Class<*> = target::class.java
  override fun getEventData(): MutableList<EventPair<*>> {
    return mutableListOf()
  }

  override fun beforeClose(reason: String?) = Unit

  override fun navigateToSingleUsageImmediately() = true

  override fun buildFinishEventData(selectedUsageInfo: UsageInfo?): MutableList<EventPair<*>> {
    return mutableListOf()
  }

  companion object {

    @JvmStatic
    fun showUsages(project: Project, searchScope: SearchScope, target: SearchTarget, parameters: ShowUsagesParameters) {
      val showTargetUsagesActionHandler = ShowTargetUsagesActionHandler(
        project,
        target = target,
        allOptions = getSearchOptions(SearchVariant.SHOW_USAGES, target, searchScope)
      )
      ShowUsagesAction.showElementUsages(parameters, showTargetUsagesActionHandler)
    }
  }
}
