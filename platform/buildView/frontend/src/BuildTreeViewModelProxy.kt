// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.*
import com.intellij.platform.buildView.BuildTreeApi
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.flow.Flow

internal sealed interface BuildTreeViewModelProxy {
  companion object {
    fun getInstance(buildViewId: BuildViewId): BuildTreeViewModelProxy {
      val modelIsLocal = buildViewId.modelIsOnClient == PlatformUtils.isJetBrainsClient()
      val localModel = if (modelIsLocal) {
        buildViewId.findValue()
      }
      else {
        null
      }
      if (localModel != null) {
        return Local(localModel)
      }
      return Remote(buildViewId)
    }
  }

  suspend fun getTreeEventsFlow(): Flow<BuildTreeEvent>
  suspend fun getFilteringStateFlow(): Flow<BuildTreeFilteringState>
  suspend fun getNavigationFlow(): Flow<BuildTreeNavigationRequest>
  suspend fun onSelectionChange(selectedNodeId: Int?)
  suspend fun onNavigationContextChange(context: BuildTreeNavigationContext)

  class Local(private val model: BuildTreeViewModel) : BuildTreeViewModelProxy {
    override suspend fun getTreeEventsFlow(): Flow<BuildTreeEvent> {
      return model.getTreeEventsFlow()
    }
    override suspend fun getFilteringStateFlow(): Flow<BuildTreeFilteringState> {
      return model.getFilteringStateFlow()
    }
    override suspend fun getNavigationFlow(): Flow<BuildTreeNavigationRequest> {
      return model.getNavigationFlow()
    }
    override suspend fun onSelectionChange(selectedNodeId: Int?) {
      model.onSelectionChange(selectedNodeId)
    }
    override suspend fun onNavigationContextChange(context: BuildTreeNavigationContext) {
      model.onNavigationContextChange(context)
    }
  }

  class Remote(private val viewId: BuildViewId) : BuildTreeViewModelProxy {
    override suspend fun getTreeEventsFlow(): Flow<BuildTreeEvent> {
      return BuildTreeApi.getInstance().getTreeEventsFlow(viewId)
    }
    override suspend fun getFilteringStateFlow(): Flow<BuildTreeFilteringState> {
      return BuildTreeApi.getInstance().getFilteringStateFlow(viewId)
    }
    override suspend fun getNavigationFlow(): Flow<BuildTreeNavigationRequest> {
      return BuildTreeApi.getInstance().getNavigationFlow(viewId)
    }
    override suspend fun onSelectionChange(selectedNodeId: Int?) {
      BuildTreeApi.getInstance().onSelectionChange(viewId, selectedNodeId)
    }
    override suspend fun onNavigationContextChange(context: BuildTreeNavigationContext) {
      BuildTreeApi.getInstance().onNavigationContextChange(viewId, context)
    }
  }
}