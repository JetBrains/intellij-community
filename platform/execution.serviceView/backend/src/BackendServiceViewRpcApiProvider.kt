// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView.backend

import com.intellij.platform.execution.serviceView.splitApi.ServiceViewRpc
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class BackendServiceViewRpcApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ServiceViewRpc>()) {
      ServiceViewRpcImpl()
    }
  }
}