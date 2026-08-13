// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.backend

import com.intellij.platform.find.FindInFilesApi
import com.intellij.platform.find.FindInFilesApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class FindInFilesApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<FindInFilesApi>()) {
      FindInFilesApiImpl()
    }
  }
}
