// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.*
import com.intellij.platform.buildView.BuildTreeApi
import com.intellij.ui.split.SplitComponentFactory
import com.intellij.ui.split.SplitComponentId
import kotlinx.coroutines.flow.Flow

internal sealed interface BuildTreeViewModelProxy {
  companion object {
    fun getInstance(viewId: SplitComponentId): BuildTreeViewModelProxy =
      when(val localModel = SplitComponentFactory.getInstance().getModel<BuildTreeViewModel>(viewId)) {
        null -> Remote(viewId)
        else -> Local(localModel)
      }
  }

  suspend fun getTreeEventsFlow(): Flow<BuildTreeEvent>
  suspend fun getFilteringStateFlow(): Flow<BuildTreeFilteringState>
  suspend fun getNavigationFlow(): Flow<BuildTreeNavigationRequest>
  suspend fun getShutdownStateFlow(): Flow<Boolean>
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
    override suspend fun getShutdownStateFlow(): Flow<Boolean> {
      return model.getShutdownStateFlow()
    }
    override suspend fun onSelectionChange(selectedNodeId: Int?) {
      model.onSelectionChange(selectedNodeId)
    }
    override suspend fun onNavigationContextChange(context: BuildTreeNavigationContext) {
      model.onNavigationContextChange(context)
    }
  }

  class Remote(private val viewId: SplitComponentId) : BuildTreeViewModelProxy {
    override suspend fun getTreeEventsFlow(): Flow<BuildTreeEvent> {
      return BuildTreeApi.getInstance().getTreeEventsFlow(viewId)
    }
    override suspend fun getFilteringStateFlow(): Flow<BuildTreeFilteringState> {
      return BuildTreeApi.getInstance().getFilteringStateFlow(viewId)
    }
    override suspend fun getNavigationFlow(): Flow<BuildTreeNavigationRequest> {
      return BuildTreeApi.getInstance().getNavigationFlow(viewId)
    }
    override suspend fun getShutdownStateFlow(): Flow<Boolean> {
      return BuildTreeApi.getInstance().getShutdownStateFlow(viewId)
    }
    override suspend fun onSelectionChange(selectedNodeId: Int?) {
      BuildTreeApi.getInstance().onSelectionChange(viewId, selectedNodeId)
    }
    override suspend fun onNavigationContextChange(context: BuildTreeNavigationContext) {
      BuildTreeApi.getInstance().onNavigationContextChange(viewId, context)
    }
  }
}