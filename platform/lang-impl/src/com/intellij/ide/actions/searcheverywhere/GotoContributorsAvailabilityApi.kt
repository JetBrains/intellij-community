// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.rpc.awaitWithLocalFallback
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * Reports whether any `gotoClassContributor` or `gotoSymbolContributor` extension is available now.
 * In remote development, the frontend calls the backend implementation.
 */
@ApiStatus.Internal
@Rpc
interface GotoContributorsAvailabilityApi : RemoteApi<Unit> {

  suspend fun hasClassContributors(projectId: ProjectId): Boolean

  suspend fun hasSymbolContributors(projectId: ProjectId): Boolean

  companion object {
    private val localInstance by lazy { GotoContributorsAvailabilityApiImpl() }

    @JvmStatic
    suspend fun getInstance(): GotoContributorsAvailabilityApi {
      return LiteRemoteApiProviderService.awaitWithLocalFallback(remoteApiDescriptor<GotoContributorsAvailabilityApi>()) { localInstance }
    }
  }
}

/**
 * Serves [GotoContributorsAvailabilityApi]: registered by the backend module for the monolith and the
 * remote-dev backend, used directly by [GotoContributorsAvailabilityApi.getInstance] in backend-less
 * frontends (IJ Light).
 */
@ApiStatus.Internal
class GotoContributorsAvailabilityApiImpl : GotoContributorsAvailabilityApi {
  override suspend fun hasClassContributors(projectId: ProjectId): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return GotoContributorsAvailabilityService.hasLocalClassContributors(project)
  }

  override suspend fun hasSymbolContributors(projectId: ProjectId): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return GotoContributorsAvailabilityService.hasLocalSymbolContributors(project)
  }
}
