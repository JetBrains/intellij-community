// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BackendMultipleBuildsView
import com.intellij.build.BuildContentId
import com.intellij.build.BuildId
import com.intellij.build.BuildViewEvent
import com.intellij.build.BuildViewViewModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BuildViewApiImpl : BuildViewApi {
  override suspend fun getBuildViewEventsFlow(projectId: ProjectId): Flow<BuildViewEvent> {
    return BuildViewViewModel.getInstance(projectId.findProject()).getFlowWithHistory()
  }

  override suspend fun setBuildContentPinned(buildContentId: BuildContentId, pinned: Boolean) {
    val content = BackendMultipleBuildsView.getById(buildContentId) ?: return
    content.pinned = pinned
  }

  override suspend fun disposeBuildContent(buildContentId: BuildContentId) {
    val content = BackendMultipleBuildsView.getById(buildContentId) ?: return
    withContext(Dispatchers.EDT) {
      Disposer.dispose(content)
    }
  }

  override suspend fun notifyTooWindowActivated(buildId: BuildId) {
    val callback = BackendMultipleBuildsView.getToolWindowActivationCallback(buildId) ?: return
    withContext(Dispatchers.EDT) {
      callback.run()
    }
  }
}