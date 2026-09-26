// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.ide.rpc

import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.runtime.product.ProductMode
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
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
  // is up. Only a strictly-Light session uses the local implementation; everything else awaits
  // (IJPL-252054). Strictly LIGHT, not isLight: LIGHT_WITH_RD_CONNECTION already holds the
  // connection and awaits it like any connected frontend.
  if (IdeProductMode.getInstance().currentMode == ProductMode.LIGHT) {
    tryResolve(descriptor)?.let { return it }
    return localImplementation()
  }
  return awaitConnectionAndResolve(descriptor)
}
