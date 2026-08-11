// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IndexingBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise


@ApiStatus.Internal
interface ScopeModelService {

  fun loadItemsAsync(
    modelId: String,
    filterConditionType: ScopesFilterConditionType,
    dataContextPromise: Promise<DataContext>,
    onScopesUpdate: suspend (Map<String, ScopeDescriptor>?, selectedScopeId: String?) -> Unit,
  )

  fun disposeModel(modelId: String)

  fun getScopeDescriptorById(scopeId: String): ScopeDescriptor?

  fun openEditScopesDialog(selectedScopeId: String?, modelId: String, onFinish: (selectedScopeId: String?) -> Unit)

  fun getCoroutineScope(): CoroutineScope

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
  FIND, TODO, OTHER;

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
      TODO -> {
        // hide the selection-based and usage view-based scopes,
        // matching ScopeChooserCombo.setCurrentSelection(false)/setUsageView(false).
        val excludedNames = setOf(
          IdeBundle.message("scope.selection"),
          IdeBundle.message("scope.previous.search.results"),
          IdeBundle.message("scope.files.in.previous.search.result"),
        )
        return scopesFilter@{ descriptor: ScopeDescriptor? ->
          val display = descriptor?.displayName ?: return@scopesFilter true
          return@scopesFilter display !in excludedNames
        }
      }
      else -> null
    }
  }
}