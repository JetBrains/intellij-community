// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BackendMultipleBuildsView
import com.intellij.build.BuildContentId
import com.intellij.build.BuildContentManager
import com.intellij.build.BuildId
import com.intellij.build.BuildViewEvent
import com.intellij.build.BuildViewViewModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import fleet.util.async.onFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BuildViewApiImpl : BuildViewApi {
  override suspend fun getBuildViewEventsFlow(projectId: ProjectId): Flow<BuildViewEvent> {
    val project = projectId.findProject()
    return BuildViewViewModel.getInstance(project).getFlowWithHistory().onFirst {
      // Create tool window on backend before reporting events to the frontend
      // (this is needed for rem-dev tool window sync mechanism to work properly),
      // but not too early, to avoid an exception in ToolWindowManager (hence the wait for 'runAfterOpened').
      suspendCancellableCoroutine { continuation ->
        StartupManager.getInstance(project).runAfterOpened {
          continuation.resumeWith(Result.success(Unit))
        }
      }
      withContext(Dispatchers.EDT) {
        BuildContentManager.getInstance(project).orCreateToolWindow
      }
    }
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