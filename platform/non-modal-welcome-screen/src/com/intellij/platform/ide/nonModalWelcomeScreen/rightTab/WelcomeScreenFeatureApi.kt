// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ide.rpc.awaitWithLocalFallback
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface WelcomeScreenFeatureApi : RemoteApi<Unit> {
  suspend fun getAvailableFeatureIds(): List<String>

  suspend fun onClick(projectId: ProjectId, featureKey: String)

  companion object {
    /**
     * Returns the api of this session. It merges the [WelcomeScreenFeatureFrontend] features into the backend features
     * by feature key. A light session has no backend, so its backend side is [NoBackendWelcomeScreenFeatureApi].
     */
    @JvmStatic
    suspend fun getInstance(): WelcomeScreenFeatureApi {
      val backendApi = LiteRemoteApiProviderService.awaitWithLocalFallback(remoteApiDescriptor<WelcomeScreenFeatureApi>()) {
        NoBackendWelcomeScreenFeatureApi
      }
      return MergedWelcomeScreenFeatureApi(backendApi)
    }
  }
}

/**
 * Merges the [WelcomeScreenFeatureFrontend] features into the features of [backendApi] by feature key. A frontend
 * feature takes the click when both sides register one key, so the click stays in this process.
 */
private class MergedWelcomeScreenFeatureApi(private val backendApi: WelcomeScreenFeatureApi) : WelcomeScreenFeatureApi {
  override suspend fun getAvailableFeatureIds(): List<String> {
    return (WelcomeScreenFeatureFrontend.getFeatureIds() + backendApi.getAvailableFeatureIds()).distinct()
  }

  override suspend fun onClick(projectId: ProjectId, featureKey: String) {
    val frontendFeature = WelcomeScreenFeatureFrontend.getForFeatureKey(featureKey)
    if (frontendFeature == null) {
      backendApi.onClick(projectId, featureKey)
      return
    }
    val project = projectId.findProject()
    withContext(Dispatchers.EDT) {
      frontendFeature.onClick(project)
    }
  }
}

/**
 * The fallback for a session without a backend. It reports no backend feature, so the welcome right tab shows only the
 * frontend features and the buttons that need no feature key.
 */
private object NoBackendWelcomeScreenFeatureApi : WelcomeScreenFeatureApi {
  override suspend fun getAvailableFeatureIds(): List<String> = emptyList()

  override suspend fun onClick(projectId: ProjectId, featureKey: String) {
    logger<WelcomeScreenFeatureApi>().warn("No backend handles the welcome screen feature $featureKey")
  }
}
