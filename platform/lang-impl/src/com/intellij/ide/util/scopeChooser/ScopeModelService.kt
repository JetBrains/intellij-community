// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IndexingBundle
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface ScopeModelService {

  fun loadItemsAsync(modelId: String, filterConditionType: ScopesFilterConditionType = ScopesFilterConditionType.OTHER, onScopesUpdate: suspend (Map<String, ScopeDescriptor>?, selectedScopeId: String?) -> Unit)

  fun disposeModel(modelId: String)

  fun getScopeById(scopeId: String): ScopeDescriptor?

  fun openEditScopesDialog(selectedScopeId: String?, onFinish: (selectedScopeId: String?) -> Unit)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ScopeModelService {
      return project.service<ScopeModelService>()
    }
  }
}

@ApiStatus.Internal
@Serializable
enum class ScopesFilterConditionType {
  FIND, OTHER;

  fun getScopeFilterByType(): ((ScopeDescriptor) -> Boolean)? {
    return when (this) {
      //moved from FindPopupScopeUIImpl
      FIND -> {
        val moduleScopeName: String = IndexingBundle.message("search.scope.module", "")
        val ind = moduleScopeName.indexOf(' ')
        val moduleFilesScopeName: String = moduleScopeName.take(ind + 1)
        return scopesFilter@{ descriptor: ScopeDescriptor? ->
          val display = descriptor?.displayName ?: return@scopesFilter true
          return@scopesFilter !display.startsWith(moduleFilesScopeName)
        }
      }
      else -> null
    }
  }
}