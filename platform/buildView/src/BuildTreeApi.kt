// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BuildTreeEvent
import com.intellij.build.BuildTreeFilteringState
import com.intellij.build.BuildTreeNavigationContext
import com.intellij.build.BuildTreeNavigationRequest
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.split.SplitComponentId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Rpc
interface BuildTreeApi : RemoteApi<Unit> {
  suspend fun getTreeEventsFlow(buildViewId: SplitComponentId): Flow<BuildTreeEvent>
  suspend fun getFilteringStateFlow(buildViewId: SplitComponentId): Flow<BuildTreeFilteringState>
  suspend fun getNavigationFlow(buildViewId: SplitComponentId): Flow<BuildTreeNavigationRequest>
  suspend fun getShutdownStateFlow(buildViewId: SplitComponentId): Flow<Boolean>

  suspend fun onSelectionChange(buildViewId: SplitComponentId, selectedNodeId: Int?)
  suspend fun onNavigationContextChange(buildViewId: SplitComponentId, context: BuildTreeNavigationContext)

  companion object {
    suspend fun getInstance(): BuildTreeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<BuildTreeApi>())
    }
  }
}