// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.rpc.ProcessHandlerApi
import com.intellij.execution.rpc.RunSessionsApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private class BackendExecutionRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ProcessHandlerApi>()) {
      BackendProcessHandlerApi()
    }
    remoteApi(remoteApiDescriptor<RunSessionsApi>()) {
      BackendRunSessionsApi()
    }
  }
}