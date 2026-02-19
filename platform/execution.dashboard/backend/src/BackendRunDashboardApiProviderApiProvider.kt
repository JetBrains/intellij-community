// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.platform.execution.dashboard.splitApi.RunDashboardManagerRpc
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class BackendRunDashboardApiProviderApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RunDashboardServiceRpc>()) {
      RunDashboardServiceRpcImpl()
    }
    remoteApi(remoteApiDescriptor<RunDashboardManagerRpc>()) {
      RunDashboardManagerRpcImpl()
    }
  }
}