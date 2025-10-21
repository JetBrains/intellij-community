// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenFeatureApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackendWelcomeScreenFeatureApi : WelcomeScreenFeatureApi {
  override suspend fun getAvailableFeatureIds(): List<String> {
    return WelcomeScreenFeatureBackend.getFeatureIds()
  }

  override suspend fun onClick(projectId: ProjectId, featureKey: String) {
    val project = projectId.findProject()
    val feature = WelcomeScreenFeatureBackend.getForFeatureKey(featureKey) ?: run {
      thisLogger().warn("Feature backend for the feature key $featureKey not found")
      return
    }
    withContext(Dispatchers.EDT) {
      feature.onClick(project)
    }
  }
}

internal class BackendWelcomeScreenFeatureApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<WelcomeScreenFeatureApi>()) {
      BackendWelcomeScreenFeatureApi()
    }
  }
}