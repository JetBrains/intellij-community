// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newCoroutineContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RunDashboardCoroutineScopeProvider(val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunDashboardCoroutineScopeProvider = project.service()
  }

  fun createChildNamedScope(name: String): CoroutineScope {
    return cs.childScope(name)
  }
}