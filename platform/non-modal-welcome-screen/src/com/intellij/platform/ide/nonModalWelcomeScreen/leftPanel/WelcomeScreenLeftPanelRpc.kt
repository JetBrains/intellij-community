// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.rpc.awaitWithLocalFallback
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface WelcomeScreenLeftPanelRpc : RemoteApi<Unit> {
  companion object {
    /**
     * Returns the api of this session. A light session has no backend, so it gets an api that builds the panel in this process.
     */
    suspend fun getInstance(): WelcomeScreenLeftPanelRpc {
      return LiteRemoteApiProviderService.awaitWithLocalFallback(remoteApiDescriptor<WelcomeScreenLeftPanelRpc>()) {
        WelcomeScreenLeftPanelRpcImpl()
      }
    }
  }

  suspend fun getComponentId(projectId: ProjectId): ComponentDirectTransferId
}
