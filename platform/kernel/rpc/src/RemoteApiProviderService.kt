// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc

import com.intellij.openapi.application.ApplicationManager
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor

interface RemoteApiProviderService {

  suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T

  companion object {
    suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T {
      return ApplicationManager.getApplication().getService(RemoteApiProviderService::class.java).resolve(descriptor)
    }
  }
}
