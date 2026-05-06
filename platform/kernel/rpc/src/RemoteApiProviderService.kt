// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc

import com.intellij.openapi.application.ApplicationManager
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

interface RemoteApiProviderService {

  suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T

  @ApiStatus.Internal
  @VisibleForTesting
  fun listRegisteredApis(): List<String>

  /**
   * Some API users that have mixed frontend and backend implementations might want to avoid performing the blocking calls
   * if the backend API is currently unavailable.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun isServiceOperational(): Boolean

  companion object {
    suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T {
      return withContext(Dispatchers.IO) {
        ApplicationManager.getApplication().getService(RemoteApiProviderService::class.java).resolve(descriptor)
      }
    }
  }
}
