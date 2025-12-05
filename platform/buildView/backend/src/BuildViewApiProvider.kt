// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.backend

import com.intellij.build.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.buildView.BuildViewApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class BuildViewApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BuildViewApi>()) {
      BuildViewApiImpl()
    }
  }
}

private class BuildViewApiImpl : BuildViewApi {
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