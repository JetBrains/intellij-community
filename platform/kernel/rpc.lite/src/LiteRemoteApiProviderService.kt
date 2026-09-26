// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.lite

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * [com.intellij.platform.rpc.RemoteApiProviderService] that allows handling backend-less environments.
 */
@ApiStatus.Experimental
interface LiteRemoteApiProviderService {
  fun isConnected(): Boolean

  fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T?

  suspend fun <T : RemoteApi<Unit>> awaitConnectionAndResolve(descriptor: RemoteApiDescriptor<T>): T

  companion object {
    fun isConnected(): Boolean {
      return service<LiteRemoteApiProviderService>().isConnected()
    }

    fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? {
      return service<LiteRemoteApiProviderService>().tryResolve(descriptor)
    }

    suspend fun <T : RemoteApi<Unit>> awaitConnectionAndResolve(descriptor: RemoteApiDescriptor<T>): T {
      return serviceAsync<LiteRemoteApiProviderService>().awaitConnectionAndResolve(descriptor)
    }
  }
}
