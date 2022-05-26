// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Compiler configuration stored in intellij.yaml file.
 * Provides default values for per-workspace configured settings
 */
class CompilerConfigurationSettings : SimplePersistentStateComponent<CompilerConfigurationSettings.State>(State()) {
  fun isParallelCompilationEnabled(): Boolean {
    return state.parallelCompilation
  }

  fun getCacheServerUrl(): String? {
    return state.cacheServerUrl
  }

  class State : BaseState() {
    var parallelCompilation by property(false)
    var cacheServerUrl by string()
  }

  companion object {
    fun getInstance(project: Project) = project.service<CompilerConfigurationSettings>()
  }
}
