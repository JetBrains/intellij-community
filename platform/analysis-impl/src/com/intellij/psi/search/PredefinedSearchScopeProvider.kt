// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

abstract class PredefinedSearchScopeProvider {
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
  abstract fun getPredefinedScopes(project: Project,
                                   dataContext: DataContext?,
                                   suggestSearchInLibs: Boolean,
                                   prevSearchFiles: Boolean,
                                   currentSelection: Boolean,
                                   usageView: Boolean,
                                   showEmptyScopes: Boolean): List<SearchScope>

  /**
   * @param suggestSearchInLibs add *Project and Libraries* scope
   * @param prevSearchFiles     add *Files in Previous Search Result* instead of *Previous Search Result* (only if `usageView == true`)
   * @param currentSelection    add *Selection* scope if text is selected in the editor
   * @param usageView           add *Previous Search Result* and *Hierarchy 'X' (visible nodes only)* scopes if there are search results or hierarchies open
   * @param showEmptyScopes     add *Current File* and *Open Files* scopes even if there are no files open
   */
  open fun getPredefinedScopesAsync(project: Project,
                                    dataContext: DataContext?,
                                    suggestSearchInLibs: Boolean,
                                    prevSearchFiles: Boolean,
                                    currentSelection: Boolean,
                                    usageView: Boolean,
                                    showEmptyScopes: Boolean): Promise<List<SearchScope>> {
    val scopes = getPredefinedScopes(project, dataContext, suggestSearchInLibs, prevSearchFiles, currentSelection, usageView,
                                     showEmptyScopes)
    return resolvedPromise(scopes)
  }

  companion object {
    @JvmStatic
    fun getInstance() = service<PredefinedSearchScopeProvider>()
  }
}
