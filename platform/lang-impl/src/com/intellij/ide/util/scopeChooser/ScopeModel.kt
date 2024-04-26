// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.WeighedItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.PredefinedSearchScopeProvider
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import java.util.function.Predicate


internal class ScopeModel(options: Set<ScopeOption>) {

  val options = mutableSetOf<ScopeOption>().apply { addAll(options) }
  private lateinit var project: Project

  fun init(project: Project) {
    if (this::project.isInitialized) {
      throw IllegalStateException("ScopeModel initialized already")
    }

    this.project = project
  }

  fun setOption(option: ScopeOption, set: Boolean) {
    if (set) {
      options.add(option)
    }
    else {
      options.remove(option)
    }
  }

  fun isSet(option: ScopeOption): Boolean {
    return options.contains(option)
  }

  fun getScopeDescriptors(filter: Predicate<in ScopeDescriptor>): Promise<ScopesSnapshot> {
    return DataManager.getInstance()
      .dataContextFromFocusAsync
      .thenAsync { dataContext ->
        getScopeDescriptors(dataContext, filter)
      }
  }

  fun getScopeDescriptors(
    dataContext: DataContext,
    filter: Predicate<in ScopeDescriptor>,
  ): Promise<ScopesSnapshot> {
    val separators: HashMap<String, ListSeparator> = HashMap()
    return PredefinedSearchScopeProvider.getInstance().getPredefinedScopesAsync(
      project, dataContext,
      options.contains(ScopeOption.LIBRARIES),
      options.contains(ScopeOption.SEARCH_RESULTS),
      options.contains(ScopeOption.FROM_SELECTION),
      options.contains(ScopeOption.USAGE_VIEW),
      options.contains(ScopeOption.EMPTY_SCOPES)
    ).then { predefinedScopes ->
      separators.clear()
      ScopesSnapshotImpl(
        doProcessScopes(project, dataContext, predefinedScopes,
                        { firstScopeName, separatorName -> separators[firstScopeName] = ListSeparator(separatorName) }, filter),
        separators
      )
    }
  }

  companion object {

    @JvmStatic
    @Deprecated("Use ScopeModel.getScopeDescriptors method instead, this method may block UI")
    fun getScopeDescriptors(project: Project, dataContext: DataContext, options: Set<ScopeOption>, filter: Predicate<in ScopeDescriptor>): ScopesSnapshot {
      val model = ScopeModel(options)
      model.init(project)
      val separators: HashMap<String, ListSeparator> = HashMap()
      val predefinedScopes = PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(
        project, dataContext,
        options.contains(ScopeOption.LIBRARIES),
        options.contains(ScopeOption.SEARCH_RESULTS),
        options.contains(ScopeOption.FROM_SELECTION),
        options.contains(ScopeOption.USAGE_VIEW),
        options.contains(ScopeOption.EMPTY_SCOPES)
      )
      return ScopesSnapshotImpl(
        doProcessScopes(
          project, dataContext, predefinedScopes,
          { firstScopeName, separatorName -> separators[firstScopeName] = ListSeparator(separatorName) },
          filter
        ),
        separators
      )
    }

    @RequiresEdt
    private fun doProcessScopes(project: Project,
                                dataContext: DataContext,
                                predefinedScopes: List<SearchScope>,
                                putToSeparatorsGroup: ((@Nls String, @NlsContexts.Separator String) -> Unit)?,
                                filter: Predicate<in ScopeDescriptor>): List<ScopeDescriptor> {
      val result = mutableListOf<ScopeDescriptor>()

      for (searchScope in predefinedScopes) {
        with(ScopeDescriptor(searchScope)) {
          if (filter.test(this)) {
            result.add(this)
          }
        }
      }

      for (provider in ScopeDescriptorProvider.EP_NAME.extensionList) {
        for (descriptor in provider.getScopeDescriptors(project, dataContext)) {
          if (filter.test(descriptor)) {
            result.add(descriptor)
          }
        }
      }

      val comparator = Comparator { o1: SearchScope, o2: SearchScope ->
        val w1 = if (o1 is WeighedItem) (o1 as WeighedItem).weight else Int.MAX_VALUE
        val w2 = if (o2 is WeighedItem) (o2 as WeighedItem).weight else Int.MAX_VALUE
        if (w1 == w2) return@Comparator StringUtil.naturalCompare(o1.displayName, o2.displayName)
        w1 - w2
      }

      for (provider in SearchScopeProvider.EP_NAME.extensions) {
        val displayName = provider.displayName
        if (StringUtil.isEmpty(displayName)) continue
        val scopes = SlowOperations.knownIssue("IDEA-304699, EA-835226").use {
          provider.getSearchScopes(project, dataContext)
        }
        if (scopes.isEmpty()) continue
        with(ScopeSeparator(displayName!!)) {
          if (filter.test(this)) {
            result.add(this)
          }
        }
        var isFirstScope = false
        for (scope in ContainerUtil.sorted(scopes, comparator)) {
          with(ScopeDescriptor(scope)) {
            if (filter.test(this)) {
              if (!isFirstScope) {
                isFirstScope = true
                putToSeparatorsGroup?.invoke(scope.displayName, displayName)
              }
              result.add(this)
            }
          }
        }
      }

      return result
    }
  }

}