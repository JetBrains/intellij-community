// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.lite

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RemoteApiProvider {
  suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T

  fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T?
}
