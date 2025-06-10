// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend.zombie

import com.intellij.platform.editor.zombie.rpc.RemoteManagedCacheApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private class RemoteManagedCacheApiImplProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteManagedCacheApi>()) {
      RemoteManagedCacheApiImpl()
    }
  }

}