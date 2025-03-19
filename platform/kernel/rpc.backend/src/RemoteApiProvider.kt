// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend

import com.intellij.openapi.extensions.ExtensionPointName
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import org.jetbrains.annotations.ApiStatus.Internal

interface RemoteApiProvider {

  interface Sink {
    fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T)
  }

  fun Sink.remoteApis()

  companion object {

    @Internal
    val EP_NAME: ExtensionPointName<RemoteApiProvider> = ExtensionPointName.create("com.intellij.platform.rpc.backend.remoteApiProvider")
  }
}
