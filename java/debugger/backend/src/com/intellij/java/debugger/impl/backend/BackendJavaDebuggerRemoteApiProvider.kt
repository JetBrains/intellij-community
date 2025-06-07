// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerLuxActionsApi
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerManagerApi
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private class BackendJavaDebuggerRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<JavaDebuggerLuxActionsApi>()) {
      BackendJavaDebuggerLuxActionsApi()
    }
    remoteApi(remoteApiDescriptor<JavaDebuggerSessionApi>()) {
      BackendJavaDebuggerSessionApi()
    }
    remoteApi(remoteApiDescriptor<JavaDebuggerManagerApi>()) {
      BackendJavaDebuggerManagerApi()
    }
  }
}