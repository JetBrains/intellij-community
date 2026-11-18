// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.lite

import com.intellij.openapi.diagnostic.thisLogger
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus

/**
 * Holds the [RemoteApiProvider] of the process while one is installed. The first installed provider stays;
 * a caller that awaits before the installation resumes with it.
 * An [uninstall] drops it, and a caller that awaits then waits for the next [install].
 */
@ApiStatus.Internal
class LiteRemoteApiProviderHolder {
  private val provider = MutableStateFlow<RemoteApiProvider?>(null)

  fun install(remoteApiProvider: RemoteApiProvider) {
    while (!provider.compareAndSet(null, remoteApiProvider)) {
      val current = provider.value ?: continue
      if (current !== remoteApiProvider) {
        thisLogger().error(
          "Remote API provider is already installed. " +
          "Tried to setup ${remoteApiProvider::class.java.simpleName}, " +
          "but ${current.javaClass.simpleName} is installed"
        )
      }
      return
    }
  }

  fun uninstall() {
    provider.value = null
  }

  fun isConnected(): Boolean {
    return provider.value != null
  }

  fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? {
    return provider.value?.tryResolve(descriptor)
  }

  suspend fun <T : RemoteApi<Unit>> awaitConnectionAndResolve(descriptor: RemoteApiDescriptor<T>): T {
    return provider.filterNotNull().first().resolve(descriptor)
  }
}
