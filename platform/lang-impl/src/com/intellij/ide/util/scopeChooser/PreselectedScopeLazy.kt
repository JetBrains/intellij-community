// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.PredefinedSearchScopeProvider
import com.intellij.psi.search.SearchScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.Optional

internal class PreselectedScopeLazy(
  private val project: Project,
  private val suggestSearchInLibs: Boolean,
  private val prevSearchWholeFiles: Boolean,
  private val selection: Any,
) {

  /**
   * - `null` — not cached yet
   * - `not-null` — cached: the [Optional] can be empty or present
   */
  private var preselectedScopeCache: Optional<SearchScope>? = null

  @RequiresEdt(generateAssertion = false)
  fun get(): SearchScope? {
    preselectedScopeCache?.let {
      return it.orElse(null)
    }
    thisLogger().info(
      "Computing scopes synchronously on EDT because ScopeChooserCombo is not initialized yet. " +
      "To avoid this, wait for initialization first: " +
      "(a) ScopeChooserCombo.waitWithModalProgressUntilInitialized(), or " +
      "(b) await the promise returned by ScopeChooserCombo.initialize()."
    )
    val scopes = runReadActionBlocking {
      PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(
        project,
        null,
        suggestSearchInLibs,
        prevSearchWholeFiles,
        false,
        false,
        false
      )
    }
    val preselectedScope = scopes.find {
      selection == it.displayName
    }
    preselectedScopeCache = Optional.ofNullable(preselectedScope)
    return preselectedScope
  }
}
