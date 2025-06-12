// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.util.io.cache.RemoteManagedCacheApi
import fleet.rpc.remoteApiDescriptor

private class RemoteManagedCacheApiImplProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteManagedCacheApi>()) {
      RemoteManagedCacheApiImpl()
    }
  }

}