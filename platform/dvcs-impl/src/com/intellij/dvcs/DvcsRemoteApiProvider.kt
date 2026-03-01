// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs

import com.intellij.dvcs.repo.VcsRepositoryColorsApiImpl
import com.intellij.dvcs.rpc.VcsRepositoryColorsApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class DvcsRemoteApiProvider: RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<VcsRepositoryColorsApi>()) {
      VcsRepositoryColorsApiImpl()
    }
  }
}