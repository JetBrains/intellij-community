// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.backend

import com.intellij.platform.buildView.BuildViewApi
import com.intellij.platform.buildView.BuildViewApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class BuildViewApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<BuildViewApi>()) {
      BuildViewApiImpl
    }
  }
}