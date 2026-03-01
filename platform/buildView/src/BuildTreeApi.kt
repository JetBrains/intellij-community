// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BuildTreeEvent
import com.intellij.build.BuildTreeNavigationContext
import com.intellij.build.BuildTreeNavigationRequest
import com.intellij.build.BuildViewId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Rpc
interface BuildTreeApi : RemoteApi<Unit> {
  suspend fun getTreeEventsFlow(buildViewId: BuildViewId): Flow<BuildTreeEvent>
  suspend fun getNavigationFlow(buildViewId: BuildViewId): Flow<BuildTreeNavigationRequest>

  suspend fun onSelectionChange(buildViewId: BuildViewId, selectedNodeId: Int?)
  suspend fun onNavigationContextChange(buildViewId: BuildViewId, context: BuildTreeNavigationContext)

  companion object {
    suspend fun getInstance(): BuildTreeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<BuildTreeApi>())
    }
  }
}