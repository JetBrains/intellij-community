// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.identifiers.highlighting.backend

import com.intellij.platform.identifiers.highlighting.shared.IdentifierHighlightingRemoteApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

private class IdentifierHighlighterMarkupRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<IdentifierHighlightingRemoteApi>()) {
      IdentifierHighlightingRemoteApiImpl()
    }
  }
}