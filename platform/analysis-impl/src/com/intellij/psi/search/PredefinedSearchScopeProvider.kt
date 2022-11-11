// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt

abstract class PredefinedSearchScopeProvider {

  companion object {

    @JvmStatic
    fun getInstance(project: Project): PredefinedSearchScopeProvider = project.service()
  }

  /**
   * Don't use this method with option `usageView ` enabled as it may cause UI freezes.
   * Prefer to use [PredefinedSearchScopeProvider.getPredefinedScopesAsync] instead wherever possible.
   *
   * @param suggestSearchInLibs add *Project and Libraries* scope
   * @param prevSearchFiles     add *Files in Previous Search Result* instead of *Previous Search Result* (only if `usageView == true`)
   * @param currentSelection    add *Selection* scope if text is selected in the editor
   * @param usageView           add *Previous Search Result* and *Hierarchy 'X' (visible nodes only)* scopes if there are search results or hierarchies open
   * @param showEmptyScopes     add *Current File* and *Open Files* scopes even if there are no files open
   */
  abstract fun getPredefinedScopes(
    dataContext: DataContext?,
    suggestSearchInLibs: Boolean,
    prevSearchFiles: Boolean,
    currentSelection: Boolean,
    usageView: Boolean,
    showEmptyScopes: Boolean,
  ): Collection<SearchScope>

  /**
   * @param suggestSearchInLibs add *Project and Libraries* scope
   * @param prevSearchFiles     add *Files in Previous Search Result* instead of *Previous Search Result* (only if `usageView == true`)
   * @param currentSelection    add *Selection* scope if text is selected in the editor
   * @param usageView           add *Previous Search Result* and *Hierarchy 'X' (visible nodes only)* scopes if there are search results or hierarchies open
   * @param showEmptyScopes     add *Current File* and *Open Files* scopes even if there are no files open
   */
  open suspend fun getPredefinedScopesAsync(
    dataContext: DataContext?,
    suggestSearchInLibs: Boolean,
    prevSearchFiles: Boolean,
    currentSelection: Boolean,
    usageView: Boolean,
    showEmptyScopes: Boolean,
  ): Collection<SearchScope> {
    return getPredefinedScopes(
      dataContext = dataContext,
      suggestSearchInLibs = suggestSearchInLibs,
      prevSearchFiles = prevSearchFiles,
      currentSelection = currentSelection,
      usageView = usageView,
      showEmptyScopes = showEmptyScopes,
    )
  }
}