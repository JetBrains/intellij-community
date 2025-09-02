// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.impl.backend

import com.intellij.java.execution.impl.shared.JavaAutoRunFloatingToolbarApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

class JavaTestDiscoveryRemoteApiProvider: RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<JavaAutoRunFloatingToolbarApi>()) {
      BackendJavaAutoRunFloatingToolbarApi()
    }
  }
}