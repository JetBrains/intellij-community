// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.impl.backend

import com.intellij.java.execution.impl.shared.JavaAutoRunFloatingToolbarApi
import com.intellij.java.execution.impl.shared.JavaAutoRunFloatingToolbarStatus
import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.flow.Flow

class BackendJavaAutoRunFloatingToolbarApi : JavaAutoRunFloatingToolbarApi {
  override suspend fun setToolbarEnabled(projectId: ProjectId, enabled: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    project.service<JavaAutoRunTrackerService>().setFloatingToolbarEnabled(enabled)
  }

  override suspend fun disableAllAutoTests(projectId: ProjectId) {
    val project = projectId.findProjectOrNull() ?: return
    project.service<JavaAutoRunTrackerService>().disableAutoTests()
  }

  override suspend fun toolbarStatus(projectId: ProjectId): Flow<JavaAutoRunFloatingToolbarStatus>? {
    val project = projectId.findProjectOrNull() ?: return null
    return project.service<JavaAutoRunTrackerService>().flow
  }
}