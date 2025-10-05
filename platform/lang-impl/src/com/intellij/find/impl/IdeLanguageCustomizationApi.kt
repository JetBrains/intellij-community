// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface IdeLanguageCustomizationApi : RemoteApi<Unit> {

  suspend fun getPrimaryIdeLanguagesExtensions(): Set<String>

  companion object {
    @JvmStatic
    suspend fun getInstance(): IdeLanguageCustomizationApi {
      return RemoteApiProviderService.Companion.resolve(remoteApiDescriptor<IdeLanguageCustomizationApi>())
    }
  }
}