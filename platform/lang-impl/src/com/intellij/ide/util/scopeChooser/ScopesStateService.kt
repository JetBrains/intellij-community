// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ScopesStateService(val project: Project) {
  private var scopesState: ScopesState? = null

  fun getScopeById(scopeId: String): SearchScope? {
    return scopesState?.scopeIdToDescriptor[scopeId]?.let { return it.scope }
  }

  fun getOrCreateScopesState(): ScopesState {
    if (scopesState != null) return scopesState!!
    val state = ScopesState(project)
    scopesState = state
    return state
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ScopesStateService {
      return project.service<ScopesStateService>()
    }
  }
}

@ApiStatus.Internal
class ScopesState internal constructor(val project: Project) {
  var scopeIdToDescriptor: Map<String, ScopeDescriptor> = mapOf()

  fun updateScopes(scopesStateMap: Map<String, ScopeDescriptor>) {
    scopeIdToDescriptor = scopesStateMap
  }
}