// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.ide.rpc

import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus

/**
 * Resolves the remote implementation, falling back to [localImplementation] in a strictly-Light
 * session - the one environment with no backend connection to await (IJPL-252054).
 */
suspend fun <T : RemoteApi<Unit>> LiteRemoteApiProviderService.Companion.awaitWithLocalFallback(
  descriptor: RemoteApiDescriptor<T>,
  localImplementation: () -> T,
): T {
  // Null is not "no backend": a connected frontend also resolves null until its protocol client
  // is up. Only a light process without a remote API uses the local implementation; everything else
  // awaits (IJPL-252054). isLightWithoutRemoteApi, not isLight: LIGHT_WITH_RD_CONNECTION already
  // holds the connection and awaits it like any connected frontend.
  if (IdeProductMode.getInstance().currentMode.isLightWithoutRemoteApi) {
    tryResolve(descriptor)?.let { return it }
    return localImplementation()
  }
  return awaitConnectionAndResolveRetrying(descriptor)
}

/**
 * Resolves [descriptor] like [awaitWithLocalFallback], but the local implementation is optional.
 *
 * In the `LIGHT` mode the call returns the backend API when a backend is connected.
 * Otherwise it returns the local implementation when [localImplementation] provides one.
 * Otherwise it suspends until a backend connects.
 * Use it when the implementation lives in an optional backend module, so the frontend part waits instead of failing.
 */
suspend fun <T : RemoteApi<Unit>> LiteRemoteApiProviderService.Companion.awaitWithOptionalLocalFallback(
  descriptor: RemoteApiDescriptor<T>,
  localImplementation: () -> T?,
): T {
  if (IdeProductMode.getInstance().currentMode.isLightWithoutRemoteApi) {
    tryResolve(descriptor)?.let { return it }
    localImplementation()?.let { return it }
  }
  return awaitConnectionAndResolveRetrying(descriptor)
}

private const val NO_REMOTE_API_RETRY_COUNT = 150
private const val NO_REMOTE_API_RETRY_DELAY_MS = 200L

/**
 * Same-process [RemoteApiProvider][com.intellij.platform.rpc.backend.RemoteApiProvider] registration races
 * the `StartupActivity` that resolves a descriptor eagerly on project open: a provider declared in a content
 * module that is still being loaded is not indexed yet, so [awaitConnectionAndResolve] fails immediately
 * instead of suspending (it only truly awaits a remote connection in split mode; locally it is one
 * synchronous lookup). Polling [tryResolve] for up to [NO_REMOTE_API_RETRY_COUNT] * [NO_REMOTE_API_RETRY_DELAY_MS]
 * (30s) bridges that startup race without changing behavior for a genuinely disconnected split-mode frontend,
 * which keeps falling through to [awaitConnectionAndResolve] to await its connection as before. This is separate
 * from (and still needed alongside) explicit classpath dependencies on optional content modules like
 * `intellij.vcs.git.rpc.backend`: those fix tools that assemble a classpath without going through plugin
 * resolution at all (e.g. PreSharedIndexesGenerator); this fixes the registration-order race that remains even
 * when the module is correctly resolved and enabled by the plugin system.
 */
private suspend fun <T : RemoteApi<Unit>> LiteRemoteApiProviderService.Companion.awaitConnectionAndResolveRetrying(
  descriptor: RemoteApiDescriptor<T>,
): T {
  repeat(NO_REMOTE_API_RETRY_COUNT) {
    tryResolve(descriptor)?.let { return it }
    delay(NO_REMOTE_API_RETRY_DELAY_MS)
  }
  return awaitConnectionAndResolve(descriptor)
}