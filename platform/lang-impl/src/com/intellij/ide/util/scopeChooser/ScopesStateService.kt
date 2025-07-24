// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ScopesStateService(val project: Project) {
  private val scopesState: ScopesState = ScopesState(project)

  fun getScopeById(scopeId: String): SearchScope? {
    try {
      return scopesState.getScopeDescriptorById(scopeId)?.scope
    } catch (e: RuntimeException) {
      // Some scopes require the Event Dispatch Thread (EDT) and cannot be loaded in the background.
      // These scopes should be filtered out earlier, but if they are not, they will be ignored here.
      @Suppress("TestOnlyProblems")
      if (e.message?.startsWith(ThreadingAssertions.MUST_EXECUTE_IN_EDT) == true) return null
      throw e
    }
  }

  fun getScopesState(): ScopesState {
    return scopesState
  }

  fun getCachedScopeDescriptors(): List<ScopeDescriptor> {
    return scopesState.scopeIdToDescriptor.values.toList()
  }

  fun getIdByScopeName(scopeName: String): String? {
    return scopesState.scopeIdToDescriptor.entries.find { it.value.displayName == scopeName }?.key
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

  // This function is primarily used on the frontend to maintain actual scope IDs with placeholder descriptors (without a SearchScope).
  // It helps prevent overriding values in monolithic environments.
  fun updateIfNotExists(scopesStateMap: Map<String, ScopeDescriptor>) {
    for ((id, descriptor) in scopesStateMap) {
      if (!scopeIdToDescriptor.containsKey(id)) {
        scopeIdToDescriptor[id] = descriptor
      }
    }
  }

  fun getScopeDescriptorById(scopeId: String): ScopeDescriptor? {
    return scopeIdToDescriptor[scopeId]
  }
}