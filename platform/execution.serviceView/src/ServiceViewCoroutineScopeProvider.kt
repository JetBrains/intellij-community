// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class ServiceViewCoroutineScopeProvider(val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ServiceViewCoroutineScopeProvider {
      return project.service<ServiceViewCoroutineScopeProvider>()
    }
  }
}