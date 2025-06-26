// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface ScopeModelService {

  fun loadItemsAsync(modelId: String, onFinished: suspend (Map<String, ScopeDescriptor>?) -> Unit)

  fun disposeModel(modelId: String)

  fun getScopeById(scopeId: String): ScopeDescriptor?

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ScopeModelService {
      return project.service<ScopeModelService>()
    }
  }
}