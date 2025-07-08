// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.backend

import com.intellij.build.*
import com.intellij.platform.buildView.BuildTreeApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.ui.split.SplitComponentFactory
import com.intellij.ui.split.SplitComponentId
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private class BuildTreeApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BuildTreeApi>()) {
      BuildTreeApiImpl()
    }
  }
}

private class BuildTreeApiImpl : BuildTreeApi {
  private fun getModel(buildViewId: SplitComponentId) = SplitComponentFactory.getInstance().getModel<BuildTreeViewModel>(buildViewId)

  override suspend fun getTreeEventsFlow(buildViewId: SplitComponentId): Flow<BuildTreeEvent> {
    val model = getModel(buildViewId) ?: return emptyFlow()
    return model.getTreeEventsFlow()
  }

  override suspend fun getFilteringStateFlow(buildViewId: SplitComponentId): Flow<BuildTreeFilteringState> {
    val model = getModel(buildViewId) ?: return emptyFlow()
    return model.getFilteringStateFlow()
  }

  override suspend fun getNavigationFlow(buildViewId: SplitComponentId): Flow<BuildTreeNavigationRequest> {
    val model = getModel(buildViewId) ?: return emptyFlow()
    return model.getNavigationFlow()
  }

  override suspend fun getShutdownStateFlow(buildViewId: SplitComponentId): Flow<Boolean> {
    val model = getModel(buildViewId) ?: return emptyFlow()
    return model.getShutdownStateFlow()
  }

  override suspend fun onSelectionChange(buildViewId: SplitComponentId, selectedNodeId: Int?) {
    val model = getModel(buildViewId) ?: return
    model.onSelectionChange(selectedNodeId)
  }

  override suspend fun onNavigationContextChange(buildViewId: SplitComponentId, context: BuildTreeNavigationContext) {
    val model = getModel(buildViewId) ?: return
    model.onNavigationContextChange(context)
  }
}