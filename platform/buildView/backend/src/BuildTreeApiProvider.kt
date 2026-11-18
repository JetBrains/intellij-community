// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.backend

import com.intellij.platform.buildView.BuildTreeApi
import com.intellij.platform.buildView.BuildTreeApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class BuildTreeApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BuildTreeApi>()) {
      BuildTreeApiImpl
    }
  }
}
