// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.ui.WindowFocusFrontendService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ScopesStateService(val project: Project) {
  private val scopesState: ScopesState = ScopesState(project)

  suspend fun getScopeById(scopeId: String): SearchScope? {
    val descriptor = scopesState.getScopeDescriptorById(scopeId) ?: return null
      return coroutineScope {
        return@coroutineScope withContext(Dispatchers.EDT) {
          WindowFocusFrontendService.getInstance().performActionWithFocus(true) {
            descriptor.scope
          }
        }
      }
    }

  fun getScopeNameById(scopeId: String): String? {
    return scopesState.getScopeDescriptorById(scopeId)?.displayName
  }

  fun getIdByScopeName(scopeName: String): String? {
    return scopesState.getIdByScopeName(scopeName)
  }

  fun getScopesState(): ScopesState {
    return scopesState
  }

  fun getCachedScopeDescriptors(): List<ScopeDescriptor> {
    return scopesState.getScopeDescriptors()
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
  private val scopeIdToDescriptor: MutableMap<String, ScopeDescriptor> = LinkedHashMap()

  fun addScope(scopeDescriptor: ScopeDescriptor): String {
    synchronized(scopeIdToDescriptor) {
      val existingIdToDescriptor = scopeIdToDescriptor.entries.find { it.value.displayName == scopeDescriptor.displayName }
      val id = existingIdToDescriptor?.key ?: UUID.randomUUID().toString()
      scopeIdToDescriptor[id] = scopeDescriptor
      return id
    }
  }

  fun updateScopes(scopesStateMap: Map<String, ScopeDescriptor>) {
    synchronized(scopeIdToDescriptor) {
      scopeIdToDescriptor.clear()
      scopeIdToDescriptor.putAll(scopesStateMap)
    }
  }

  // This function is primarily used on the frontend to maintain actual scope IDs with placeholder descriptors (without a SearchScope).
  // It helps prevent overriding values in monolithic environments.
  fun updateIfNotExists(scopesStateMap: Map<String, ScopeDescriptor>) {
    synchronized(scopeIdToDescriptor) {
      for ((id, descriptor) in scopesStateMap) {
        if (!scopeIdToDescriptor.containsKey(id)) {
          scopeIdToDescriptor[id] = descriptor
        }
      }
    }
  }

  fun getScopeDescriptors(): List<ScopeDescriptor> {
    synchronized(scopeIdToDescriptor) {
      return scopeIdToDescriptor.values.toList()
    }
  }

  fun getScopeDescriptorById(scopeId: String): ScopeDescriptor? {
    synchronized(scopeIdToDescriptor) {
      return scopeIdToDescriptor[scopeId]
    }
  }

  fun getIdByScopeName(scopeName: String): String? {
    synchronized(scopeIdToDescriptor) {
      return scopeIdToDescriptor.firstNotNullOfOrNull { if (it.value.displayName == scopeName) it.key else null }
    }
  }
}