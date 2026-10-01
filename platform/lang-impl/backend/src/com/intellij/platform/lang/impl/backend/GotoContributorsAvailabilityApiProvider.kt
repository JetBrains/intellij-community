// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.impl.backend

import com.intellij.ide.actions.searcheverywhere.GotoContributorsAvailabilityApi
import com.intellij.ide.actions.searcheverywhere.GotoContributorsAvailabilityApiImpl
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

internal class GotoContributorsAvailabilityApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<GotoContributorsAvailabilityApi>()) {
      GotoContributorsAvailabilityApiImpl()
    }
  }
}
