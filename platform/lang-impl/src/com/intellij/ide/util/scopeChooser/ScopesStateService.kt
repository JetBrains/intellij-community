// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ScopesStateService(val project: Project) {
  private var scopesState: ScopesState? = null

  fun getScopeById(scopeId: String): SearchScope? {
    return scopesState?.getScopeDescriptorById(scopeId)?.let { return it.scope }
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
  val scopeIdToDescriptor: MutableMap<String, ScopeDescriptor> = mutableMapOf()

  fun addScope(scopeDescriptor: ScopeDescriptor): String {
    val existingIdToDescriptor = scopeIdToDescriptor.entries.find { it.value.displayName == scopeDescriptor.displayName }
    val id = existingIdToDescriptor?.key ?: UUID.randomUUID().toString()
    scopeIdToDescriptor[id] = scopeDescriptor
    return id
  }

  fun updateScopes(scopesStateMap: Map<String, ScopeDescriptor>) {
    scopeIdToDescriptor.clear()
    scopeIdToDescriptor.putAll(scopesStateMap)
  }

  fun getScopeDescriptorById(scopeId: String): ScopeDescriptor? {
    return scopeIdToDescriptor[scopeId]
  }
}