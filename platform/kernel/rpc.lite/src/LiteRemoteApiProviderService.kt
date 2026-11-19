// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.lite

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * [com.intellij.platform.rpc.RemoteApiProviderService] that allows handling backend-less environments.
 *
 * The provider comes from [RemoteApiProvider.EP_NAME]. The extension is present at the start of a process that owns
 * an RPC backend. It appears when such a module loads without a restart, which is how the JetBrains Light moves to
 * the monolith mode and to the frontend mode. The extension goes away when that module unloads, which is how
 * JetBrains Light moves back to the light mode. The service is then not connected again. A process with no
 * extension is not connected: [tryResolve] returns `null` and [awaitConnectionAndResolve] waits for the extension.
 * `awaitWithLocalFallback` is the way around that.
 */
@Service
@ApiStatus.Experimental
class LiteRemoteApiProviderService @ApiStatus.Internal @VisibleForTesting constructor(coroutineScope: CoroutineScope) {
  private val holder = LiteRemoteApiProviderHolder()

  init {
    RemoteApiProvider.EP_NAME.addChangeListener(coroutineScope) { syncProvider() }
    syncProvider()
  }

  private fun syncProvider() {
    val extension = RemoteApiProvider.EP_NAME.extensionList.firstOrNull()
    if (extension == null) holder.uninstall() else holder.install(extension)
  }

  fun isConnected(): Boolean {
    return holder.isConnected()
  }

  fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? {
    return holder.tryResolve(descriptor)
  }

  suspend fun <T : RemoteApi<Unit>> awaitConnectionAndResolve(descriptor: RemoteApiDescriptor<T>): T {
    return holder.awaitConnectionAndResolve(descriptor)
  }

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
