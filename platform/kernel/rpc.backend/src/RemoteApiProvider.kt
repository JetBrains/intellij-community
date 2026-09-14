// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend

import com.intellij.openapi.extensions.ExtensionPointName
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Registers the backend implementations of [Rpc][fleet.rpc.Rpc] interfaces.
 *
 * Declare the provider in the backend module descriptor, and list every interface it registers in `apiInterfaces`:
 * ```xml
 * <platform.rpc.backend.remoteApiProvider
 *     implementation="com.example.backend.MyRemoteApiProvider"
 *     apiInterfaces="com.example.shared.MyRemoteApi, com.example.shared.OtherRemoteApi"/>
 * ```
 *
 * The registry routes a call by the API FQN from the attribute, so the provider class is loaded and the provider is constructed
 * on the first call to one of its APIs. The registry reports an error when the attribute and [remoteApis] disagree.
 */
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
