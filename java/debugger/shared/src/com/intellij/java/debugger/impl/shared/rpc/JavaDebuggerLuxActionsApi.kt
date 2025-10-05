// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.rpc.XValueId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

@Rpc
interface JavaDebuggerLuxActionsApi : RemoteApi<Unit> {
  suspend fun showInstancesDialog(xValueId: XValueId)
  suspend fun showCalculateRetainedSizeDialog(xValueId: XValueId, nodeName: String)
  suspend fun showCreateRendererDialog(xValueId: XValueId)
  suspend fun showCustomizeDataViewsDialog(projectId: ProjectId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): JavaDebuggerLuxActionsApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<JavaDebuggerLuxActionsApi>())
    }
  }
}