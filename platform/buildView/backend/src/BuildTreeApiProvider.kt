// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.backend

import com.intellij.build.BuildTreeEvent
import com.intellij.build.BuildTreeNavigationContext
import com.intellij.build.BuildTreeNavigationRequest
import com.intellij.build.BuildViewId
import com.intellij.build.findValue
import com.intellij.platform.buildView.BuildTreeApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class BuildTreeApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BuildTreeApi>()) {
      BuildTreeApiImpl()
    }
  }
}

private class BuildTreeApiImpl : BuildTreeApi {
  override suspend fun getTreeEventsFlow(buildViewId: BuildViewId): Flow<BuildTreeEvent> {
    val model = buildViewId.findValue() ?: return emptyFlow()
    return model.getTreeEventsFlow()
  }

  override suspend fun getNavigationFlow(buildViewId: BuildViewId): Flow<BuildTreeNavigationRequest> {
    val model = buildViewId.findValue() ?: return emptyFlow()
    return model.getNavigationFlow()
  }

  override suspend fun onSelectionChange(buildViewId: BuildViewId, selectedNodeId: Int?) {
    val model = buildViewId.findValue() ?: return
    model.onSelectionChange(selectedNodeId)
  }

  override suspend fun onNavigationContextChange(buildViewId: BuildViewId, context: BuildTreeNavigationContext) {
    val model = buildViewId.findValue() ?: return
    model.onNavigationContextChange(context)
  }
}