// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface WelcomeScreenFeatureApi : RemoteApi<Unit> {
  suspend fun getAvailableFeatureIds(): List<String>

  suspend fun onClick(projectId: ProjectId, featureKey: String)

  companion object {
    @JvmStatic
    suspend fun getInstance(): WelcomeScreenFeatureApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<WelcomeScreenFeatureApi>())
    }
  }
}